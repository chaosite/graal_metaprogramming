package il.ac.technion.cs.mipphd.graal.graphquery;

import edu.umd.cs.findbugs.annotations.NonNull;
import il.ac.technion.cs.mipphd.graal.utils.CFGWrapper;
import il.ac.technion.cs.mipphd.graal.utils.GraalIRGraph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.jgrapht.graph.DirectedPseudograph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;
import org.jgrapht.nio.dot.DOTImporter;

import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class GraphQuery extends DirectedPseudograph<GraphQueryVertex, GraphQueryEdge> {
    public GraphQuery() {
        super(
                () -> GraphQueryVertex.fromQuery("true"),
                () -> GraphQueryEdge.fromQuery("true"),
                false);
    }

    protected Stream<Map<GraphQueryVertex, List<AnalysisNode>>> _match(AnalysisGraph cfg) {
        return GenericBFSKt.bfsMatch(this, cfg, this.startCandidate(cfg)).stream()
                .map(v -> (Map<GraphQueryVertex, List<AnalysisNode>>) v);
    }

    public List<Map<GraphQueryVertex, List<AnalysisNode>>> match(GraalIRGraph cfg) {
        return match(AnalysisGraph.Companion.fromIR(cfg));
    }

    public List<Map<GraphQueryVertex, List<AnalysisNode>>> match(AnalysisGraph graph) {
        return _match(graph).toList();
    }


    public List<Map<GraphQueryVertex, List<AnalysisNode>>> match(ControlFlowGraph cfg) {
        return match(GraalIRGraph.fromGraal(cfg));
    }

    public List<Map<GraphQueryVertex, List<AnalysisNode>>> match(CFGWrapper cfg) {
        return match(cfg.asCFG());
    }

    public <T extends Node> GraphQueryVertex addVertex(String mQuery) {
        final GraphQueryVertex v = GraphQueryVertex.fromQuery(mQuery);
        this.addVertex(v);
        return v;
    }

    public <T extends Node, S extends Node> GraphQueryEdge addEdge(GraphQueryVertex source, GraphQueryVertex destination, GraphQueryEdgeType type, GraphQueryEdgeMatchType matchType) {
        final GraphQueryEdge e = new GraphQueryEdge(type, matchType);
        this.addEdge(source, destination, e);
        return e;
    }

    private GraphQueryVertex startCandidate(AnalysisGraph cfg) {
        final Set<AnalysisNode> nodes = new HashSet<>(cfg.vertexSet());
        Optional<GraphQueryVertex> root = this.vertexSet().stream()
                .filter(v -> !((Metadata) v.getMQuery()).getOptions().contains(MetadataOption.Repeated.INSTANCE) )
                .filter(v -> this.inDegreeOf(v) == 0).findAny();
        if (root.isPresent())
            return root.get();
        // TODO: Improve this more
        Map<Long, List<GraphQueryVertex>> histogram = this.vertexSet().stream()
                .filter(v -> !((Metadata) v.getMQuery()).getOptions().contains(MetadataOption.Repeated.INSTANCE) )
                .collect(Collectors.groupingBy(v -> nodes
                        .stream()
                        .filter(n -> n instanceof AnalysisNode.IR)
                        .map(n -> (AnalysisNode.IR) n)
                        .map(AnalysisNode.IR::node).filter(v::match).count()));
        System.out.println(histogram);
        return histogram.get(histogram.keySet().stream().min(Comparator.naturalOrder()).get()).get(0);
    }

    public void exportQuery(Writer output) {
        DOTExporter<GraphQueryVertex, GraphQueryEdge> exporter =
                new DOTExporter<>(GraphQueryVertex::getName);

        exporter.setVertexAttributeProvider(v -> {
            final Map<String, Attribute> attrs = new HashMap<>();
            attrs.put("label", DefaultAttribute.createAttribute(v.label()));
            return attrs;
        });

        exporter.setEdgeAttributeProvider(e -> {
            final Map<String, Attribute> attrs = new HashMap<>();
            attrs.put("label", DefaultAttribute.createAttribute(e.label()));
            return attrs;
        });
        exporter.exportGraph(this, output);
    }

    @NonNull
    public static GraphQuery importQuery(@NonNull Reader input) {
        GraphQuery ret = new GraphQuery();
        DOTImporter<GraphQueryVertex, GraphQueryEdge> importer = new DOTImporter<>();
        importer.setVertexFactory(GraphQueryVertex::fromName);
        importer.addVertexAttributeConsumer(((p, a) -> {
            if (p.getSecond().equals("label")) {
                GraphQueryVertex v = p.getFirst();
                v.setMQuery(MQueryKt.parseMQuery(a.getValue()));
            }
        }));
        importer.addEdgeAttributeConsumer(((p, a) -> {
            if (p.getSecond().equals("label")) {
                GraphQueryEdge e = p.getFirst();
                e.setMQuery(MQueryKt.parseMQuery(a.getValue()));
            }
        }));
        importer.importGraph(ret, input);
        return ret;
    }

    @NonNull
    public static GraphQuery importQuery(@NonNull String input) {
        return GraphQuery.importQuery(new StringReader(input));
    }
}
