package il.ac.technion.cs.mipphd.graal.utils;


import edu.umd.cs.findbugs.annotations.NonNull;
import il.ac.technion.cs.mipphd.graal.MethodToGraph;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Class to wrap {@link ResolvedJavaMethod} so it can be accessed from Kotlin.
 */
public class MethodWrapper {
    @NonNull
    private final ResolvedJavaMethod resolvedJavaMethod;

    public MethodWrapper(ResolvedJavaMethod method) {
        resolvedJavaMethod = method;
    }

    @NonNull
    public ResolvedJavaMethod getResolvedJavaMethod() {
        return resolvedJavaMethod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodWrapper that = (MethodWrapper) o;
        return resolvedJavaMethod.equals(that.resolvedJavaMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resolvedJavaMethod);
    }

    public CFGWrapper toCFG(MethodToGraph methodToGraph) { return methodToGraph.getCFGFromWrapper(this); }

    @Override
    public String toString() {
        return "MethodWrapper{" + resolvedJavaMethod + '}';
    }
}
