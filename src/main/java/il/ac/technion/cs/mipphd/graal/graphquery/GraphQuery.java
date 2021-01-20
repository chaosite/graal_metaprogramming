package il.ac.technion.cs.mipphd.graal.graphquery;

import il.ac.technion.cs.mipphd.graal.GraalAdapter;
import il.ac.technion.cs.mipphd.graal.graphquery.GenericBFSKt;
import il.ac.technion.cs.mipphd.graal.utils.CFGWrapper;
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper;
import org.graalvm.compiler.graph.NodeInterface;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.jetbrains.annotations.Nullable;
import org.jgrapht.graph.DirectedPseudograph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;

import java.io.Writer;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;


public class GraphQuery extends DirectedPseudograph<GraphQueryVertex<? extends NodeInterface>, GraphQueryEdge> {
    public GraphQuery() {
        super(
                () -> new GraphQueryVertex<>(NodeInterface.class, o -> true),
                () -> new GraphQueryEdge(GraphQueryEdgeType.BOTH, GraphQueryEdgeMatchType.NORMAL, whatever -> true),
                false);
    }

    public List<Map<GraphQueryVertex<? extends NodeInterface>, NodeWrapper>> match(ControlFlowGraph cfg) {
        return GenericBFSKt.bfsMatch(this, GraalAdapter.fromGraal(cfg), this.leastCommonVertex(cfg)).stream()
                .map(v -> (Map<GraphQueryVertex<? extends NodeInterface>, NodeWrapper>) v)
                .collect(Collectors.toUnmodifiableList());
    }

    public List<Map<GraphQueryVertex<? extends NodeInterface>, NodeWrapper>> match(CFGWrapper cfg) {
        return match(cfg.asCFG());
    }

    public <T extends NodeInterface> GraphQueryVertex<T> addVertex(Class<T> clazz) {
        return this.addVertex(clazz, o -> true);
    }

    public <T extends NodeInterface> GraphQueryVertex<T> addVertex(Class<T> clazz, Predicate<T> p) {
        final GraphQueryVertex<T> v = new GraphQueryVertex<>(clazz, p);
        this.addVertex(v);
        return v;
    }

    public <T extends NodeInterface, S extends NodeInterface> GraphQueryEdge addEdge(GraphQueryVertex<T> source, GraphQueryVertex<S> destination, GraphQueryEdgeType type, GraphQueryEdgeMatchType matchType, Predicate<Object> p) {
        final GraphQueryEdge e = new GraphQueryEdge(type, matchType, p);
        this.addEdge(source, destination, e);
        return e;
    }

    public <T extends NodeInterface, S extends NodeInterface> GraphQueryEdge addEdge(GraphQueryVertex<T> source, GraphQueryVertex<S> destination, GraphQueryEdgeType type, GraphQueryEdgeMatchType matchType) {
        return this.addEdge(source, destination, type, matchType, o -> true);
    }

    private GraphQueryVertex<? extends NodeInterface> leastCommonVertex(ControlFlowGraph cfg) {
        final Set<NodeInterface> nodes = new HashSet<>();
        cfg.graph.getNodes().forEach(nodes::add);
        // TODO: Improve this, filtering out the ReturnNodes isn't good enough.
        Map<Long, List<GraphQueryVertex<? extends NodeInterface>>> histogram = this.vertexSet().stream().filter(v -> !ReturnNode.class.isAssignableFrom(v.getClazz())).collect(Collectors.groupingBy(v -> nodes.stream().filter(v::match).count()));
        return histogram.get(histogram.keySet().stream().min(Comparator.naturalOrder()).get()).get(0);
    }

    void exportQuery(Writer output) {
        DOTExporter<GraphQueryVertex<? extends NodeInterface>, GraphQueryEdge> exporter =
                new DOTExporter<>(v -> "n" + v.hashCode());

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
}
