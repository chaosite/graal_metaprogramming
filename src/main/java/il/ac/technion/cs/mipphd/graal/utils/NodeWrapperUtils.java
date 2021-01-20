package il.ac.technion.cs.mipphd.graal.utils;

import org.graalvm.compiler.nodes.Invoke;

public class NodeWrapperUtils {
    public static boolean isInvoke(NodeWrapper wrapper) {
        return wrapper.getNode().asNode() instanceof Invoke;
    }

    public static MethodWrapper getTargetMethod(NodeWrapper wrapper) {
        assert isInvoke(wrapper);
        Invoke invoke = (Invoke) wrapper.getNode().asNode();
        return new MethodWrapper(invoke.getTargetMethod());
    }
}
