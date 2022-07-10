package il.ac.technion.cs.mipphd.graal.utils;

import il.ac.technion.cs.mipphd.graal.graphquery.GraphQuery;
import il.ac.technion.cs.mipphd.graal.graphquery.GraphQueryEdge;
import il.ac.technion.cs.mipphd.graal.graphquery.GraphQueryVertex;
import kotlin.Pair;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInterface;
import org.graalvm.compiler.graph.Position;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.jetbrains.annotations.NotNull;
import org.jgrapht.alg.util.Triple;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DirectedPseudograph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static il.ac.technion.cs.mipphd.graal.utils.EdgeWrapper.CONTROL;

public class GraalAdapter extends DirectedPseudograph<NodeWrapper, EdgeWrapper> {
    public static final Map<String, String> edgeColor = Map.of(EdgeWrapper.DATA, "blue",
            CONTROL, "red",
            EdgeWrapper.ASSOCIATED, "black");
    public static final Map<String, String> edgeStyle = Map.of(EdgeWrapper.DATA, "",
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
    public void exportSubgraph(Writer output, AsSubgraph<NodeWrapper,EdgeWrapper> subgraph){
        DOTExporter<NodeWrapper, EdgeWrapper> exporter =
                new DOTExporter<>(v -> Integer.toString(v.getNode().asNode().getId()));

        exporter.setVertexAttributeProvider(v -> {
            final Map<String, Attribute> attrs = new HashMap<>();
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
            return attrs;
        });

        exporter.exportGraph(subgraph, output);

    }

    public void exportMatch(Writer output, List<Integer> matchedPorts){
        DOTExporter<NodeWrapper, EdgeWrapper> exporter =
                new DOTExporter<>(v -> Integer.toString(v.getNode().asNode().getId()));

        exporter.setVertexAttributeProvider(v -> {
            final Map<String, Attribute> attrs = new HashMap<>();
            if(matchedPorts.contains(v.getId())){
                attrs.put("fontsize", DefaultAttribute.createAttribute(50));
                attrs.put("shape", DefaultAttribute.createAttribute("box"));
                attrs.put("fillcolor", DefaultAttribute.createAttribute("green"));
                attrs.put("style", DefaultAttribute.createAttribute("filled"));
            }
            attrs.put("label", DefaultAttribute.createAttribute(v.getNode().toString()));
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
//            var source = this.getEdgeSource(e);
//            var target = this.getEdgeTarget(e);
//            if(matches.contains(source) && matches.contains(target)){
//                attrs.put("penwidth", DefaultAttribute.createAttribute(10));
//            }
            return attrs;
        });
        exporter.exportGraph(this, output);
    }


    public void exportQuery(Writer output, Map<GraphQueryVertex, List<NodeWrapper>> matches) {
        exportQuery(output, matches,null);
    }
    public void exportQuery(Writer output, Map<GraphQueryVertex, List<NodeWrapper>> matches, List<GraphQuery> queries) {

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

            //matched nodes
            if(vmap.containsKey(v)){
//                label += " \n Matched with: " + vmap.get(v).stream().map(qqv -> qqv.label()).reduce((gqv1,gqv2) -> gqv1 + " & " + gqv2);
                attrs.put("style", DefaultAttribute.createAttribute("filled"));
                attrs.put("fillcolor", DefaultAttribute.createAttribute("lightgray"));
                if(vmap.get(v).stream().anyMatch(qqv -> qqv.captureGroup().isPresent())){
                    attrs.put("color", DefaultAttribute.createAttribute("#00ff00"));
                }
                if(this.outgoingEdgesOf(v).stream().anyMatch(e -> e.label.equals(CONTROL))){
                    attrs.put("shape", DefaultAttribute.createAttribute("box"));
                    attrs.put("fillcolor", DefaultAttribute.createAttribute("#ff00005f"));
                }
            }
            //non matched none
            else{
                attrs.put("style", DefaultAttribute.createAttribute("filled"));
                attrs.put("fillcolor", DefaultAttribute.createAttribute("#0000009"));
                attrs.put("color", DefaultAttribute.createAttribute("#000005f"));
            }
            //default attr
            attrs.put("label", DefaultAttribute.createAttribute(label));


            //return
            if(v.toString().contains("Return")){
                attrs.put("fillcolor", DefaultAttribute.createAttribute("red"));
                attrs.put("style", DefaultAttribute.createAttribute("filled"));
            }
            return attrs;
        });

        exporter.setEdgeAttributeProvider(e -> {
            final Map<String, Attribute> attrs = new HashMap<>();
            if(e.label.contains("QueryEdge")){
                var color = e.label.split("[|]")[1];
                attrs.put("color", DefaultAttribute.createAttribute(color));
                attrs.put("penwidth", DefaultAttribute.createAttribute(10));
                //attrs.put("dir",DefaultAttribute.createAttribute("none"));
                attrs.put("label", DefaultAttribute.createAttribute(e.getName()));
            }
            else{
                attrs.put("label", DefaultAttribute.createAttribute(e.getName()));
                attrs.put("color", DefaultAttribute.createAttribute(edgeColor.get(e.getLabel())));
                attrs.put("style", DefaultAttribute.createAttribute(edgeStyle.get(e.getLabel())));
                attrs.put("penwidth", DefaultAttribute.createAttribute(1));
            }

            return attrs;
        });
        if(queries == null){
            exporter.exportGraph(this, output);
            return;
        }
        var sb = new StringBuilder();
        sb.append("subgraph cluster_legend {\n");
        var colors = Colors().iterator();
        var i = 0;
        for(var query : queries){
            var color = colors.next();
            var id = query.name.toLowerCase(Locale.ROOT);
            sb.append(String.format("%s [ label=\"%s\" style=\"filled\" fillcolor=\"%s\"];\n",id.replaceAll(" ","_") + i++ ,query.name,color));
        }
        sb.append("}\n\n");

        var sw = new StringWriter();
        exporter.exportGraph(this,sw);
        var graph = sw.toString().replaceFirst("digraph G", "subgraph Graal");

        var res = "digraph G {\n"+ sb + graph + "}";
        try {
            output.write(res);
            output.flush();
            //System.out.println(res);

        } catch (IOException e) {
            e.printStackTrace();
        }
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
                var mergeNodeEdge = clone.outgoingEdgesOf(endNode).stream().filter(e -> e.label.equals(CONTROL)).findFirst();
                if(mergeNodeEdge.isEmpty()){
                   continue;
                }
                NodeWrapper mergeNode = clone.getEdgeTarget(mergeNodeEdge.get());

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

    public GraalAdapter annotateGraphWithQuery(@NotNull List<AsSubgraph<NodeWrapper, EdgeWrapper>> subgraphs) {
        var annotatedGraph = (GraalAdapter)this.clone();
        var colors = Colors().iterator();
        for(var subgraph : subgraphs){
            var color = colors.next();
            for(var e : subgraph.edgeSet()){
                var s = subgraph.getEdgeSource(e);
                var t = subgraph.getEdgeTarget(e);
                annotatedGraph.addEdge(s,t,new EdgeWrapper("QueryEdge|"+color,e.name));
            }
        }
        return annotatedGraph;
    }

    public void exportQuerySubgraph(Writer output, @NotNull List<Pair<GraphQuery,Map<GraphQueryVertex<? extends NodeInterface>, List<NodeWrapper>>>> querisMatches) {
        var clusters = new ArrayList<String>();
        var subgraphs = new ArrayList<AsSubgraph<NodeWrapper,EdgeWrapper>>();
        var colors = Colors().iterator();
        Integer id = 0;
        for(var queryMatches : querisMatches){
            var query = queryMatches.getFirst();
            var ms = queryMatches.getSecond().values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
            var querySubGraph = new AsSubgraph<NodeWrapper, EdgeWrapper>(this,ms, new HashSet<>());
            subgraphs.add(querySubGraph);
            DOTExporter<NodeWrapper, EdgeWrapper> exporter =
                    new DOTExporter<>(v -> Integer.toString(v.getNode().asNode().getId()));
            Integer finalId = id;
            id++;
            var queryNameValid = query.name.replaceAll("[ ,]","_");
            exporter.setGraphIdProvider(() -> "cluster_"+queryNameValid+ finalId.toString());
            exporter.setGraphAttributeProvider(() -> {
                        final Map<String, Attribute> attrs = new HashMap<>();
                        attrs.put("label",DefaultAttribute.createAttribute("\"" + queryNameValid + "\""));
                        attrs.put("style",DefaultAttribute.createAttribute("\"filled\""));
                        attrs.put("color",DefaultAttribute.createAttribute("\"" +colors.next()+ "\""));
                        return attrs;
            }
            );
            exporter.setVertexAttributeProvider(v -> {
                final Map<String, Attribute> attrs = new HashMap<>();
                var label = v.getNode().toString();
                //default attr
                attrs.put("label", DefaultAttribute.createAttribute(label));
                return attrs;
            });


            var sw = new StringWriter();
            exporter.exportGraph(querySubGraph,sw);
            var subGraphDot = sw.toString().replaceFirst("digraph", "subgraph");
            clusters.add(subGraphDot);
        }

        DOTExporter<NodeWrapper, EdgeWrapper> exporter =
                new DOTExporter<>(v -> Integer.toString(v.getNode().asNode().getId()));

        exporter.setVertexAttributeProvider(v -> {
            final Map<String, Attribute> attrs = new HashMap<>();
            var label = v.getNode().toString();
            //default attr
            attrs.put("label", DefaultAttribute.createAttribute(label));
            return attrs;
        });

        exporter.setEdgeAttributeProvider(e -> {
            final Map<String, Attribute> attrs = new HashMap<>();
                attrs.put("label", DefaultAttribute.createAttribute(e.getName()));
                attrs.put("color", DefaultAttribute.createAttribute(edgeColor.get(e.getLabel())));
                attrs.put("style", DefaultAttribute.createAttribute(edgeStyle.get(e.getLabel())));

                var source = this.getEdgeSource(e);
                var target = this.getEdgeTarget(e);
                var querySource = subgraphs.stream().filter(s -> s.containsVertex(source)).collect(Collectors.toUnmodifiableList());
                var queryTarget = subgraphs.stream().filter(s -> s.containsVertex(target)).collect(Collectors.toUnmodifiableList());
                if(!querySource.isEmpty() && !queryTarget.isEmpty()){
                    attrs.put("penwidth", DefaultAttribute.createAttribute(5));
                }
            return attrs;
        });

        var sw = new StringWriter();
        exporter.exportGraph(this, sw);
        var clustersString = String.join("\n", clusters);
        var result = sw.toString().replaceFirst("[{]","{\n" + clustersString);
        //System.out.println(result);
        try {
            output.write(result);
            output.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<String> Colors(){
        return Arrays.asList(
                "#ff00005f",
                "#ff91005f",
                "#ffe6005f",
                "#c3ff005f",
                "#77ff005f",
                "#00ffd95f",
                "#f7d5d55f",
                "#00a6ff5f",
                "#a600ff5f",
                "#ff00fb5f",
                "#a6768e5f",
                "#6767875f",
                "#67877f5f",
                "#7587675f",
                "#877e675f",
                "#706d6c5f"
        );
    }
}
