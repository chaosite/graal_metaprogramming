package il.ac.technion.cs.mipphd.graal.graphquery;

import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper;
import org.graalvm.compiler.graph.NodeInterface;

import java.util.function.Predicate;

public class GraphQueryVertex<T extends NodeInterface> {
    private final Class<T> clazz;
    private Predicate<NodeInterface> predicate;

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
    public void setPredicate(Predicate<? extends NodeInterface> predicate) { this.predicate = (Predicate<NodeInterface>) predicate;}

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
        return "is('" + clazz.getCanonicalName().replaceFirst("org[.]graalvm[.]compiler[.]nodes[.]", "") + "')";
    }

    /* do not override equals/hashCode! */
}
