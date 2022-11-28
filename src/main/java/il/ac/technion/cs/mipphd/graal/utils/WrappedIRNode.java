package il.ac.technion.cs.mipphd.graal.utils;

import org.graalvm.compiler.graph.Node;

public interface WrappedIRNode {
    Node node();

    Boolean isType(String className);

    String getId();
}
