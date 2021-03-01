package il.ac.technion.cs.mipphd.graal.utils;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Position;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
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

public class GraalAdapter extends DirectedPseudograph<NodeWrapper, EdgeWrapper> {
    private static final Map<String, String> edgeColor = Map.of(EdgeWrapper.DATA, "blue",
            EdgeWrapper.CONTROL, "red",
            EdgeWrapper.ASSOCIATED, "black");
    private static final Map<String, String> edgeStyle = Map.of(EdgeWrapper.DATA, "",
            EdgeWrapper.CONTROL, "",
            EdgeWrapper.ASSOCIATED, "dashed");

    public GraalAdapter() {
        super(() -> new NodeWrapper(null), () -> new EdgeWrapper("", ""), false);
    }

    public static GraalAdapter fromGraal(CFGWrapper cfg) {
        return fromGraal(cfg.asCFG());
    }

    public static GraalAdapter fromGraal(ControlFlowGraph cfg) {
        GraalAdapter g = new GraalAdapter();
        for (Node n : cfg.graph.getNodes()) {
            g.addVertex(new NodeWrapper(n)); // does not add duplicates
        }
        for (Node u : cfg.graph.getNodes()) {
            for (Node v : u.cfgSuccessors()) {
                boolean success = false;
                for (Position position : u.successorPositions()) {
                    if (null != position.get(u) && position.get(u).equals(v)) {
                        g.addEdge(new NodeWrapper(u), new NodeWrapper(v), new EdgeWrapper(EdgeWrapper.CONTROL, position.getName()));
                        success = true;
                    }
                }
                if (!success)
                    g.addEdge(new NodeWrapper(u), new NodeWrapper(v), new EdgeWrapper(EdgeWrapper.CONTROL, "???"));
            }
            for (Node v : StreamSupport.stream(u.usages().spliterator(), false).distinct().collect(Collectors.toUnmodifiableList())) {
                boolean success = false;
                for (Position position : v.inputPositions()) {
                    if (null != position.get(v) && position.get(v).equals(u)) {
                        if (position.getName().equals("loopBegin") && v instanceof LoopEndNode) {
                            g.addEdge(new NodeWrapper(v), new NodeWrapper(u), new EdgeWrapper(EdgeWrapper.CONTROL, "?loop"));
                            g.addEdge(new NodeWrapper(u), new NodeWrapper(v), new EdgeWrapper(EdgeWrapper.ASSOCIATED, position.getName()));
                        } else if (position.getName().equals("loopBegin") && v instanceof LoopExitNode) {
                            g.addEdge(new NodeWrapper(u), new NodeWrapper(v), new EdgeWrapper(EdgeWrapper.ASSOCIATED, position.getName()));
                        } else if (position.getName().equals("values") && v instanceof ValuePhiNode) {
                            ValuePhiNode phi = (ValuePhiNode) v;
                            Node phiSource =
                                    Stream.concat(StreamSupport.stream(phi.merge().inputs().spliterator(), false),
                                            StreamSupport.stream(phi.merge().usages().spliterator(), false))
                                            .filter(n -> n instanceof LoopEndNode || n instanceof EndNode)
                                            .collect(Collectors.toList())
                                            .get(position.getSubIndex());
                            g.addEdge(new NodeWrapper(u), new NodeWrapper(v), new PhiEdgeWrapper(EdgeWrapper.DATA, "from " + phiSource.getId(), new NodeWrapper(phiSource)));
                        } else {
                            g.addEdge(new NodeWrapper(u), new NodeWrapper(v), new EdgeWrapper(EdgeWrapper.DATA, position.getName()));
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
        DOTExporter<NodeWrapper, EdgeWrapper> exporter =
                new DOTExporter<>(v -> Integer.toString(v.getNode().asNode().getId()));

        exporter.setVertexAttributeProvider(v -> {
            final Map<String, Attribute> attrs = new HashMap<>();
            attrs.put("label", DefaultAttribute.createAttribute(v.getNode().toString()));
            return attrs;
        });

        exporter.setEdgeAttributeProvider(e -> {
            final Map<String, Attribute> attrs = new HashMap<>();
            attrs.put("label", DefaultAttribute.createAttribute(e.getName()));
            attrs.put("color", DefaultAttribute.createAttribute(edgeColor.get(e.getLabel())));
            attrs.put("style", DefaultAttribute.createAttribute(edgeStyle.get(e.getLabel())));
            return attrs;
        });
        exporter.exportGraph(this, output);
    }

    public Set<EdgeWrapper> incomingEdgesOf(NodeWrapper vertex, String edgeName, String edgeLabel) {
        return super.incomingEdgesOf(vertex)
                .stream().filter(v -> v.name.equals(edgeName) && v.label.equals(edgeLabel)).collect(Collectors.toSet());
    }

    public Set<EdgeWrapper> outgoingEdgesOf(NodeWrapper vertex, String edgeName, String edgeLabel) {
        return super.outgoingEdgesOf(vertex)
                .stream().filter(v -> v.name.equals(edgeName) && v.label.equals(edgeLabel)).collect(Collectors.toSet());
    }
}
