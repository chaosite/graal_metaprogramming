package il.ac.technion.cs.mipphd.graal.utils;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Position;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.*;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.jgrapht.graph.DirectedPseudograph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;

import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class GraalIRGraph extends DirectedPseudograph<WrappedIRNodeImpl, WrappedIREdge> {
    private static final Map<String, String> edgeColor = Map.of(WrappedIREdge.DATA, "blue",
            WrappedIREdge.CONTROL, "red",
            WrappedIREdge.ASSOCIATED, "black");
    private static final Map<String, String> edgeStyle = Map.of(WrappedIREdge.DATA, "",
            WrappedIREdge.CONTROL, "",
            WrappedIREdge.ASSOCIATED, "dashed");

    public GraalIRGraph() {
        super(() -> new WrappedIRNodeImpl(null), () -> new WrappedIREdge("", ""), false);
    }

    public static GraalIRGraph fromGraal(CFGWrapper cfg) {
        return fromGraal(cfg.asCFG());
    }

    public static GraalIRGraph fromGraal(ControlFlowGraph cfg) {
        GraalIRGraph g = new GraalIRGraph();
        for (Node n : cfg.graph.getNodes()) {
            g.addVertex(new WrappedIRNodeImpl(n)); // does not add duplicates
        }
        for (Node u : cfg.graph.getNodes()) {
            for (Node v : u.cfgSuccessors()) {
                boolean success = false;
                for (Position position : u.successorPositions()) {
                    if (null != position.get(u) && position.get(u).equals(v)) {
                        g.addEdge(new WrappedIRNodeImpl(u), new WrappedIRNodeImpl(v), new WrappedIREdge(WrappedIREdge.CONTROL, position.getName()));
                        success = true;
                    }
                }
                // So far this case has always been an EndNode to its successor
                if (!success)
                    g.addEdge(new WrappedIRNodeImpl(u), new WrappedIRNodeImpl(v), new WrappedIREdge(WrappedIREdge.CONTROL, "next"));
            }
            for (Node v : StreamSupport.stream(u.usages().spliterator(), false).distinct().toList()) {
                boolean success = false;
                for (Position position : v.inputPositions()) {
                    if (null != position.get(v) && position.get(v).equals(u)) {
                        if (position.getName().equals("loopBegin") && v instanceof LoopEndNode) {
                            g.addEdge(new WrappedIRNodeImpl(v), new WrappedIRNodeImpl(u), new WrappedIREdge(WrappedIREdge.CONTROL, "next"));
                            g.addEdge(new WrappedIRNodeImpl(u), new WrappedIRNodeImpl(v), new WrappedIREdge(WrappedIREdge.ASSOCIATED, position.getName()));
                        } else if (position.getName().equals("loopBegin") && v instanceof LoopExitNode) {
                            g.addEdge(new WrappedIRNodeImpl(u), new WrappedIRNodeImpl(v), new WrappedIREdge(WrappedIREdge.ASSOCIATED, position.getName()));
                        } else if (position.getName().equals("values") && v instanceof ValuePhiNode phi) {
                            Node phiSource =
                                    Stream.concat(StreamSupport.stream(phi.merge().inputs().spliterator(), false),
                                                    StreamSupport.stream(phi.merge().usages().spliterator(), false))
                                            .filter(n -> n instanceof LoopEndNode || n instanceof EndNode).toList()
                                            .get(position.getSubIndex());
                            g.addEdge(new WrappedIRNodeImpl(u), new WrappedIRNodeImpl(v), new WrappedIRPhiEdge(WrappedIREdge.DATA, "from " + phiSource.toString(Verbosity.Id), new WrappedIRNodeImpl(phiSource)));
                        } else if (position.getName().equals("values") && v instanceof FrameState) {
                            /* Do nothing in this case, these edges are polluting the graph and I don't think I need them */
                            /* TODO: Maybe do add them? As a special type of edge can perhaps ignore? */
                            g.addEdge(new WrappedIRNodeImpl(u), new WrappedIRNodeImpl(v), new WrappedIREdge(WrappedIREdge.ASSOCIATED, position.getName()));
                        } else if (position.getName().equals("ends") && u instanceof EndNode) {
                            /* There already is a "next" edge, so we don't need this edge */
                        } else if ((position.getName().equals("stateAfter") && u instanceof FrameState) ||
                                (position.getName().equals("merge") && v instanceof ValuePhiNode) ||
                                (!position.getName().equals("value") && v instanceof ValueProxyNode)) {
                            g.addEdge(new WrappedIRNodeImpl(u), new WrappedIRNodeImpl(v), new WrappedIREdge(WrappedIREdge.ASSOCIATED, position.getName()));
                        } else {
                            g.addEdge(new WrappedIRNodeImpl(u), new WrappedIRNodeImpl(v), new WrappedIREdge(WrappedIREdge.DATA, position.getName()));
                        }
                        success = true;
                    }
                }
                assert (success); // TODO: Reasonable?
            }
        }
        return g;
    }

    public void exportQuery(Writer output) {
        DOTExporter<WrappedIRNodeImpl, WrappedIREdge> exporter =
                new DOTExporter<>(v -> v.node().toString(Verbosity.Id));

        exporter.setVertexAttributeProvider(v -> {
            final Map<String, Attribute> attrs = new HashMap<>();
            attrs.put("label", DefaultAttribute.createAttribute(v.node().toString()));
            return attrs;
        });

        exporter.setEdgeAttributeProvider(e -> {
            final Map<String, Attribute> attrs = new HashMap<>();
            attrs.put("label", DefaultAttribute.createAttribute(e.getLabel()));
            attrs.put("color", DefaultAttribute.createAttribute(edgeColor.get(e.getKind())));
            attrs.put("style", DefaultAttribute.createAttribute(edgeStyle.get(e.getKind())));
            return attrs;
        });
        exporter.exportGraph(this, output);
    }

    public Set<WrappedIREdge> incomingEdgesOf(WrappedIRNodeImpl vertex, String edgeName, String edgeLabel) {
        return super.incomingEdgesOf(vertex)
                .stream().filter(v -> v.label.equals(edgeName) && v.kind.equals(edgeLabel)).collect(Collectors.toSet());
    }

    public Set<WrappedIREdge> outgoingEdgesOf(WrappedIRNodeImpl vertex, String edgeName, String edgeLabel) {
        return super.outgoingEdgesOf(vertex)
                .stream().filter(v -> v.label.equals(edgeName) && v.kind.equals(edgeLabel)).collect(Collectors.toSet());
    }
}
