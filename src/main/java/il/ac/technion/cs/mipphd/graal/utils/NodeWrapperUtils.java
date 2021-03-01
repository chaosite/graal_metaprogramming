package il.ac.technion.cs.mipphd.graal.utils;

import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LogicNode;
import org.jetbrains.annotations.NotNull;

public class NodeWrapperUtils {
    public static boolean isInvoke(NodeWrapper wrapper) {
        return wrapper.getNode().asNode() instanceof Invoke;
    }
    public static boolean isConstant(NodeWrapper wrapper) { return wrapper.getNode().asNode() instanceof ConstantNode; }
    public static boolean isLogic(NodeWrapper wrapper) { return wrapper.getNode().asNode() instanceof LogicNode; }

    @NotNull
    public static MethodWrapper getTargetMethod(@NotNull NodeWrapper wrapper) {
        assert isInvoke(wrapper);
        Invoke invoke = (Invoke) wrapper.getNode().asNode();
        return new MethodWrapper(invoke.getTargetMethod());
    }

    @NotNull
    public static String getConstantValue(@NotNull NodeWrapper wrapper) {
        assert isConstant(wrapper);
        ConstantNode constant = (ConstantNode) wrapper.getNode().asNode();
        return constant.getValue().toValueString();
    }

    @NotNull
    public static String getLogicText(@NotNull NodeWrapper node) {
        assert isLogic(node);
        LogicNode logic = (LogicNode) node.getNode().asNode();
        throw new RuntimeException("Not implemented");
    }
}
