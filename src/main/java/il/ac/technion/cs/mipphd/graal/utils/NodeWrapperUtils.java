package il.ac.technion.cs.mipphd.graal.utils;

import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LogicNode;
import org.jetbrains.annotations.NotNull;

public class NodeWrapperUtils {
    public static boolean isInvoke(NodeWrapper wrapper) {
        return wrapper.getNode() instanceof Invoke;
    }
    public static boolean isConstant(NodeWrapper wrapper) { return wrapper.getNode() instanceof ConstantNode; }
    public static boolean isLogic(NodeWrapper wrapper) { return wrapper.getNode() instanceof LogicNode; }

    @NotNull
    public static MethodWrapper getTargetMethod(@NotNull NodeWrapper wrapper) {
        assert isInvoke(wrapper);
        Invoke invoke = (Invoke) wrapper.getNode();
        return new MethodWrapper(invoke.getTargetMethod());
    }

    @NotNull
    public static String getConstantValue(@NotNull NodeWrapper wrapper) {
        assert isConstant(wrapper);
        ConstantNode constant = (ConstantNode) wrapper.getNode();
        return constant.getValue().toValueString();
    }

    @NotNull
    public static String getLogicText(@NotNull NodeWrapper node) {
        assert isLogic(node);
        LogicNode logic = (LogicNode) node.getNode();
        throw new RuntimeException("Not implemented");
    }
}
