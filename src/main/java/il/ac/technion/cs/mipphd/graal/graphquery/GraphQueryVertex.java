package il.ac.technion.cs.mipphd.graal.graphquery;

import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper;
import org.graalvm.compiler.graph.NodeInterface;

import java.util.function.Predicate;

public class GraphQueryVertex<T extends NodeInterface> {
    private final Class<T> clazz;
    private final Predicate<NodeInterface> predicate;

    @SuppressWarnings("unchecked")
    public static <T extends NodeInterface> GraphQueryVertex<?> parse(String clazz) {
        try {
            return new GraphQueryVertex<>((Class<T>) Class.forName(clazz), v -> true);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Blahh", e);
        }
    }

    @SuppressWarnings("unchecked")
    public GraphQueryVertex(Class<T> clazz, Predicate<T> predicate) {
        this.clazz = clazz;
        this.predicate = (Predicate<NodeInterface>) predicate;
    }

    public Class<T> getClazz() {
        return clazz;
    }

    public Predicate<NodeInterface> getPredicate() {
        return predicate;
    }

    public boolean match(NodeInterface value) {
        if (this.clazz.isAssignableFrom(value.getClass())) {
            return this.predicate.test(value);
        }
        return false;
    }

    public boolean match(NodeWrapper value) {
        return match(value.getNode());
    }

    @Override
    public String toString() {
        return "GraphQueryVertex{" +
                "clazz=" + clazz +
                '}';
    }

    public String label() {
        return clazz.getCanonicalName();
    }

    /* do not override equals/hashCode! */
}
