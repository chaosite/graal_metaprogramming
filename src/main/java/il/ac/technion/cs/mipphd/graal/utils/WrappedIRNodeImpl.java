package il.ac.technion.cs.mipphd.graal.utils;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.Verbosity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WrappedIRNodeImpl implements WrappedIRNode {
    Set<String> memoized = new HashSet<>();
    private final Node node;

    public WrappedIRNodeImpl(Node node) {
        this.node = node;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        WrappedIRNodeImpl that = (WrappedIRNodeImpl) o;

        return new EqualsBuilder().append(node, that.node).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(node).toHashCode();
    }

    @Override
    public String toString() {
        return "NodeWrapper{" +
                "node=" + node +
                '}';
    }

    public String shortToString() {
        return node.toString(Verbosity.Long);
    }

    @Override
    public Node node() {
        return node;
    }

    public Boolean isType(String className) {
        if (memoized.contains(className))
            return true;
        boolean foundClass = false;
        String basePackage = "org.graalvm.compiler.nodes";
        // https://stackoverflow.com/questions/15893174/list-all-subpackages-of-a-package
        List<Package> packages = Arrays.stream(Package.getPackages())
                .filter(p -> p.getName().startsWith(basePackage))
                .toList();
        for (Package p : packages) {
            try {
                Class<?> clazz = Class.forName(p.getName() + "." + className);
                foundClass = true;
                if (clazz.isAssignableFrom(node.getClass())) {
                    memoized.add(className);
                    return true;
                }
            } catch (ClassNotFoundException e) {
                // ignore
            }

        }
        if (!foundClass) {
            // TODO: Do nothing instead?
            throw new RuntimeException("No such class " + className);
        }
        return false;
    }

    public String getId() {
        return this.node.toString(Verbosity.Id);
    }
}
