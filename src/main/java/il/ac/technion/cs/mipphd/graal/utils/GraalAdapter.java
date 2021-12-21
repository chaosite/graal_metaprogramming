package il.ac.technion.cs.mipphd.graal.utils;

import il.ac.technion.cs.mipphd.graal.graphquery.GraphQuery;
import il.ac.technion.cs.mipphd.graal.graphquery.GraphQueryEdge;
import il.ac.technion.cs.mipphd.graal.graphquery.GraphQueryVertex;
import kotlin.Pair;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Position;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.jgrapht.alg.util.Triple;
import org.jgrapht.graph.DirectedPseudograph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static il.ac.technion.cs.mipphd.graal.utils.EdgeWrapper.CONTROL;

public class GraalAdapter extends DirectedPseudograph<NodeWrapper, EdgeWrapper> {
    private static final Map<String, String> edgeColor = Map.of(EdgeWrapper.DATA, "blue",
            CONTROL, "red",
            EdgeWrapper.ASSOCIATED, "black");
    private static final Map<String, String> edgeStyle = Map.of(EdgeWrapper.DATA, "",
            CONTROL, "",
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
                        g.addEdge(new NodeWrapper(u), new NodeWrapper(v), new EdgeWrapper(CONTROL, position.getName()));
                        success = true;
                    }
                }
                if (!success)
                    g.addEdge(new NodeWrapper(u), new NodeWrapper(v), new EdgeWrapper(CONTROL, "???"));
            }
            for (Node v : StreamSupport.stream(u.usages().spliterator(), false).distinct().collect(Collectors.toUnmodifiableList())) {
                boolean success = false;
                for (Position position : v.inputPositions()) {
                    if (null != position.get(v) && position.get(v).equals(u)) {
                        if (position.getName().equals("loopBegin") && v instanceof LoopEndNode) {
                            g.addEdge(new NodeWrapper(v), new NodeWrapper(u), new EdgeWrapper(CONTROL, "?loop"));
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

    public void exportQuery(Writer output, List<NodeWrapper> matches) {

        DOTExporter<NodeWrapper, EdgeWrapper> exporter =
                new DOTExporter<>(v -> Integer.toString(v.getNode().asNode().getId()));

        exporter.setVertexAttributeProvider(v -> {
            final Map<String, Attribute> attrs = new HashMap<>();
            if(matches.stream().anyMatch(p -> p.equals(v))){
                attrs.put("fontsize", DefaultAttribute.createAttribute(50));
            }
            attrs.put("label", DefaultAttribute.createAttribute(v.getNode().toString()));
            if(v.toString().contains("Return")){
                attrs.put("fillcolor", DefaultAttribute.createAttribute("red"));
                attrs.put("style", DefaultAttribute.createAttribute("filled"));
            }
            return attrs;
        });

        exporter.setEdgeAttributeProvider(e -> {
            final Map<String, Attribute> attrs = new HashMap<>();
            attrs.put("label", DefaultAttribute.createAttribute(e.getName()));
            attrs.put("color", DefaultAttribute.createAttribute(edgeColor.get(e.getLabel())));
            attrs.put("style", DefaultAttribute.createAttribute(edgeStyle.get(e.getLabel())));
//            if(e.getLabel().equals(CONTROL)){
//                attrs.put("penwidth ", DefaultAttribute.createAttribute("2"));
//            }
            var source = this.getEdgeSource(e);
            var target = this.getEdgeTarget(e);
            if(matches.contains(source) && matches.contains(target)){
                attrs.put("penwidth", DefaultAttribute.createAttribute(10));
            }
            return attrs;
        });
        exporter.exportGraph(this, output);
    }
    public void exportQuery(Writer output, GraphQuery query, Map<GraphQueryVertex, List<NodeWrapper>> matches) {

        DOTExporter<NodeWrapper, EdgeWrapper> exporter =
                new DOTExporter<>(v -> Integer.toString(v.getNode().asNode().getId()));
        var vmap = new HashMap<NodeWrapper, ArrayList<GraphQueryVertex>>();
        matches.forEach((gqv,ns) -> {
            ns.forEach(n -> {
                if(!vmap.containsKey(n)){
                    vmap.put(n,new ArrayList<GraphQueryVertex>());
                }
                vmap.get(n).add(gqv);
            });
        });
        exporter.setVertexAttributeProvider(v -> {
            final Map<String, Attribute> attrs = new HashMap<>();
            var label = v.getNode().toString();
            if(vmap.containsKey(v)){
                label += " \n Matched with: " + vmap.get(v).stream().map(qqv -> qqv.label()).reduce((gqv1,gqv2) -> gqv1 + " & " + gqv2);
                if(vmap.get(v).stream().anyMatch(qqv -> qqv.captureGroup().isPresent())){
                    attrs.put("style", DefaultAttribute.createAttribute("filled"));
                    attrs.put("fillcolor", DefaultAttribute.createAttribute("#00ff005f"));
                }
            }
            attrs.put("label", DefaultAttribute.createAttribute(label));
            if(v.toString().contains("Return")){
                attrs.put("fillcolor", DefaultAttribute.createAttribute("red"));
                attrs.put("style", DefaultAttribute.createAttribute("filled"));
            }
            return attrs;
        });

        exporter.setEdgeAttributeProvider(e -> {
            final Map<String, Attribute> attrs = new HashMap<>();
            attrs.put("label", DefaultAttribute.createAttribute(e.getName()));
            attrs.put("color", DefaultAttribute.createAttribute(edgeColor.get(e.getLabel())));
            attrs.put("style", DefaultAttribute.createAttribute(edgeStyle.get(e.getLabel())));
            var source = this.getEdgeSource(e);
            var target = this.getEdgeTarget(e);
            if(vmap.containsKey(source) && vmap.containsKey(target)){
                if(query == null){
                    attrs.put("penwidth", DefaultAttribute.createAttribute(10));
                }
                else{
                    var querySources = vmap.get(source);
                    var queryTargets = vmap.get(target);
                    for (GraphQueryVertex querySource : querySources) {
                        Set<GraphQueryEdge> nexts = query.outgoingEdgesOf(querySource);

                        var targets = nexts.stream().map(n -> query.getEdgeTarget(n)).collect(Collectors.toList());
                        if (nexts.stream().map(GraphQueryEdge::label).anyMatch(s -> s.contains("*|"))){
                            //todo deal somehow with kleene edges
                            attrs.put("penwidth", DefaultAttribute.createAttribute(10));
                        }
                        if(targets.stream().anyMatch(v -> queryTargets.contains(v))){
                            attrs.put("penwidth", DefaultAttribute.createAttribute(10));
                        }
                    }
                }

            }

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

    public void simplify(){
        DirectedPseudograph<NodeWrapper, EdgeWrapper> clone = (DirectedPseudograph<NodeWrapper, EdgeWrapper>)this.clone();
        for (NodeWrapper node : clone.vertexSet()) {
            var n = node.toString();
            if(n.contains("FrameState")
                    || n.contains("Exception")
                    || n.contains("KillingBegin")
                    || n.contains("ValueProxy")
                    || n.contains("Unwind")
            ){
                if(!n.contains("Exception") && !n.contains("FrameState")){

                var outgoing = this.outgoingEdgesOf(node);
                var incoming = this.incomingEdgesOf(node);
                var edgesToAdd = new ArrayList<Triple<NodeWrapper,NodeWrapper,EdgeWrapper>>();
                for (var incomingEdge : incoming) {
                    for (var outgoingEdge : outgoing) {
                        var incomingSource = this.getEdgeSource(incomingEdge);
                        var outgoingTarget = this.getEdgeTarget(outgoingEdge);
                        edgesToAdd.add(new Triple<>(incomingSource,outgoingTarget,new EdgeWrapper(outgoingEdge.label, outgoingEdge.name)));
                    }
                }
                for (var edge: edgesToAdd) {
                    this.addEdge(edge.getFirst(),edge.getSecond(),edge.getThird());
                }
                }

                this.removeVertex(node);
            }
        }
        System.out.println("Removed total of " + ((long) clone.vertexSet().size() - (long) this.vertexSet().size()) + " vertices," +
                " and total number of " + ((long) clone.edgeSet().size() - (long) this.edgeSet().size()) + " edges.");
    }
    public boolean removeLeaves(){
        var removed = false;
        DirectedPseudograph<NodeWrapper, EdgeWrapper> clone = (DirectedPseudograph<NodeWrapper, EdgeWrapper>)this.clone();
        for (NodeWrapper node : clone.vertexSet()) {
            if(clone.outgoingEdgesOf(node).size() == 0 && !node.toString().contains("Return")){
                this.removeVertex(node);
                removed = true;
            }
        }
        return  removed;
    }

    public void removeLeavesRec(){
        int depth = 10;
        while(removeLeaves() && depth-->0);
    }
    public void removeLoopUnrolls(){
        DirectedPseudograph<NodeWrapper, EdgeWrapper> clone = (DirectedPseudograph<NodeWrapper, EdgeWrapper>)this.clone();
        for (NodeWrapper node : clone.vertexSet()) {
            if(!node.toString().contains("LoopBegin")) continue;
            NodeWrapper loopExitNode = null;
                for(var e : clone.outgoingEdgesOf(node)){
                    var v = clone.getEdgeTarget(e);
                    if(v.toString().contains("LoopExit")){
                        loopExitNode = v;
                        break;
                    }
            }
                if(loopExitNode == null){
                    System.out.println("Failed to find loop exit node");
                    break;
                }
                //ugly
                NodeWrapper endNode = clone.getEdgeTarget(clone.outgoingEdgesOf(loopExitNode).stream().filter(e -> e.label.equals(CONTROL)).findFirst().get());
                var mergeNodeEdge = clone.outgoingEdgesOf(endNode).stream().filter(e -> e.label.equals(CONTROL)).findFirst().get();
                NodeWrapper mergeNode = clone.getEdgeTarget(mergeNodeEdge);

                NodeWrapper otherEndNode = clone.getEdgeSource(clone.incomingEdgesOf(mergeNode).stream().filter(e -> e.getLabel().equals(CONTROL) && !e.equals(mergeNodeEdge)).findFirst().get());
                EdgeWrapper unrolledBeginIfEdge = clone.incomingEdgesOf(otherEndNode).stream().findFirst().get();
                NodeWrapper unrolledBeginIf = clone.getEdgeSource(unrolledBeginIfEdge);
                EdgeWrapper unrolledIfEdge = clone.incomingEdgesOf(unrolledBeginIf).stream().findFirst().get();
                NodeWrapper unrolledIf = clone.getEdgeSource(clone.incomingEdgesOf(unrolledBeginIf).stream().findFirst().get());
                //should be the other branch of the if
                NodeWrapper pathNode = clone.getEdgeTarget(clone.outgoingEdgesOf(unrolledIf).stream().filter(e -> e.getLabel().equals(CONTROL) && !e.equals(unrolledIfEdge)).findFirst().get());
                List<NodeWrapper> nodesToRemove = new ArrayList<>();
                for(var parentLoopNode : clone.incomingEdgesOf(unrolledIf).stream().filter(e -> e.getLabel().equals(CONTROL)).map(clone::getEdgeSource).collect(Collectors.toList())){
                    this.addEdge(parentLoopNode,node, new EdgeWrapper(CONTROL, "next"));
                }
                nodesToRemove.add(unrolledIf);
                nodesToRemove.add(otherEndNode);
                nodesToRemove.add(unrolledBeginIf);
                var queue = new LinkedList<NodeWrapper>();
                queue.add(pathNode);
                while(queue.size() > 0){
                    //remove all unrolled control until we reach our loop begin
                    pathNode = queue.poll();
                    if(pathNode.equals(node)) continue;
                    nodesToRemove.add(pathNode);
                    queue.addAll(clone.outgoingEdgesOf(pathNode).stream()
                            .filter(e -> e.getLabel().equals(CONTROL)).map(e -> clone.getEdgeTarget(e)).collect(Collectors.toList()));
                }
            for (var v: nodesToRemove) {
                this.removeVertex(v);
            }

        }
    }
}
