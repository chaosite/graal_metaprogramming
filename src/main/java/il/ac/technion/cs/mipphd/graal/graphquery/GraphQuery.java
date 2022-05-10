package il.ac.technion.cs.mipphd.graal.graphquery;

import edu.umd.cs.findbugs.annotations.NonNull;
import il.ac.technion.cs.mipphd.graal.utils.CFGWrapper;
import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter;
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ReturnNode;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class GraphQuery extends DirectedPseudograph<GraphQueryVertex<? extends Node>, GraphQueryEdge> {
    public GraphQuery() {
        super(
                () -> GraphQueryVertexM.fromQuery("1 = 1"),
                () -> GraphQueryEdge.fromQuery("1 = 1"),
                false);
    }

    public List<Map<GraphQueryVertex<? extends Node>, List<NodeWrapper>>> match(GraalAdapter cfg) {
        return _match(cfg).collect(Collectors.toUnmodifiableList());
    }

    protected Stream<Map<GraphQueryVertex<? extends Node>, List<NodeWrapper>>> _match(GraalAdapter cfg) {
        return GenericBFSKt.bfsMatch(this, cfg, this.startCandidate(cfg)).stream()
                .map(v -> (Map<GraphQueryVertex<? extends Node>, List<NodeWrapper>>) v);
    }

    public List<Map<GraphQueryVertex<? extends Node>, List<NodeWrapper>>> match(ControlFlowGraph cfg) {
        return match(GraalAdapter.fromGraal(cfg));
    }

    public List<Map<GraphQueryVertex<? extends Node>, List<NodeWrapper>>> match(CFGWrapper cfg) {
        return match(cfg.asCFG());
    }

    public <T extends Node> GraphQueryVertex<T> addVertex(Class<T> clazz) {
        return this.addVertex(clazz, o -> true);
    }

    public <T extends Node> GraphQueryVertex<T> addVertex(Class<T> clazz, Predicate<T> p) {
        final GraphQueryVertex<T> v = new GraphQueryVertex<>(clazz, p);
        this.addVertex(v);
        return v;
    }

    public <T extends Node, S extends Node> GraphQueryEdge addEdge(GraphQueryVertex<T> source, GraphQueryVertex<S> destination, GraphQueryEdgeType type, GraphQueryEdgeMatchType matchType) {
        final GraphQueryEdge e = new GraphQueryEdge(type, matchType);
        this.addEdge(source, destination, e);
        return e;
    }

    private GraphQueryVertex<? extends Node> startCandidate(GraalAdapter cfg) {
        final Set<NodeWrapper> nodes = new HashSet<>(cfg.vertexSet());
        Optional<GraphQueryVertex<? extends Node>> root = this.vertexSet().stream().filter(v -> this.inDegreeOf(v) == 0).findAny();
        if (root.isPresent())
            return root.get();
        // TODO: Improve this, filtering out the ReturnNodes isn't good enough.
        Map<Long, List<GraphQueryVertex<? extends Node>>> histogram = this.vertexSet().stream().filter(v -> !ReturnNode.class.isAssignableFrom(v.getClazz())).collect(Collectors.groupingBy(v -> nodes.stream().map(NodeWrapper::getNode).filter(v::match).count()));
        return histogram.get(histogram.keySet().stream().min(Comparator.naturalOrder()).get()).get(0);
    }

    public void exportQuery(Writer output) {
        DOTExporter<GraphQueryVertex<? extends Node>, GraphQueryEdge> exporter =
                new DOTExporter<>(v -> v.getName() );

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
        DOTImporter<GraphQueryVertex<? extends Node>, GraphQueryEdge> importer = new DOTImporter<>();
        importer.setVertexFactory(GraphQueryVertexM::fromName);
        importer.addVertexAttributeConsumer(((p, a) -> {
            if (p.getSecond().equals("label")) {
                GraphQueryVertexM v = (GraphQueryVertexM) p.getFirst();
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
