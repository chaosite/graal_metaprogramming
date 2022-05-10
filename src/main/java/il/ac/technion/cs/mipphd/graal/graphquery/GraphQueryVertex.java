package il.ac.technion.cs.mipphd.graal.graphquery;

import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper;
import org.graalvm.compiler.graph.Node;

import java.util.Optional;
import java.util.function.Predicate;

public class GraphQueryVertex<T extends Node> {
    private final Class<T> clazz;
    private Predicate<Node> predicate;
    private String name = "n" + this.hashCode();

    @SuppressWarnings("unchecked")
    public GraphQueryVertex(Class<T> clazz, Predicate<T> predicate) {
        this.clazz = clazz;
        this.predicate = (Predicate<Node>) predicate;
    }

    public void setName(String name) { this.name = name; }
    public String getName() { return name; }

    public Class<T> getClazz() {
        return clazz;
    }

    @SuppressWarnings("unchecked")
    public void setPredicate(Predicate<? extends Node> predicate) {
        this.predicate = (Predicate<Node>) predicate;
    }

    public boolean match(Node value) {
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
        return "is('" + clazz.getCanonicalName().replaceFirst("org[.]graalvm[.]compiler[.]nodes[.]", "") + "')";
    }

    public Optional<String> captureGroup() {
        return Optional.empty();
    }

    /* do not override equals/hashCode! */
}
