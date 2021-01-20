package il.ac.technion.cs.mipphd.graal.utils;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.graalvm.compiler.graph.NodeInterface;

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
}
