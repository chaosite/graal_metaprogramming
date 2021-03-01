package il.ac.technion.cs.mipphd.graal.utils;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.graalvm.compiler.graph.NodeInterface;

import java.util.List;

public class NodeWrapper {
    private final NodeInterface node;

    public NodeWrapper(NodeInterface node) {
        this.node = node;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        NodeWrapper that = (NodeWrapper) o;

        return new EqualsBuilder().append(node, that.node).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(node).toHashCode();
    }

    public NodeInterface getNode() {
        return node;
    }

    @Override
    public String toString() {
        return "NodeWrapper{" +
                "node=" + node +
                '}';
    }

    public Boolean isType(String className) {
        try {
            Class<?> cls = Class.forName("org.graalvm.compiler.nodes." + className);
            return cls.isAssignableFrom(this.getNode().getClass());
        } catch (ClassNotFoundException e) {
            // TODO: Do nothing instead?
            throw new RuntimeException("No such class", e);
        }
    }

    public int getId() {
        return this.node.asNode().getId();
    }
}
