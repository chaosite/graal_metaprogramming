package il.ac.technion.cs.mipphd.graal.utils;

import org.graalvm.compiler.graph.Node;
import org.jetbrains.annotations.NotNull;

public interface WrappedIRNode {
    Node node();

    Boolean isType(String className);

    String getId();

    @NotNull
    String getType();
}
