package il.ac.technion.cs.mipphd.graal.utils;

import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LogicNode;
import org.jetbrains.annotations.NotNull;

public class NodeWrapperUtils {
    public static boolean isInvoke(WrappedIRNode wrapper) {
        return wrapper.node() instanceof Invoke;
    }

    public static boolean isConstant(WrappedIRNode wrapper) {
        return wrapper.node() instanceof ConstantNode;
    }

    public static boolean isLogic(WrappedIRNode wrapper) {
        return wrapper.node() instanceof LogicNode;
    }

    @NotNull
    public static MethodWrapper getTargetMethod(@NotNull WrappedIRNode wrapper) {
        assert isInvoke(wrapper);
        Invoke invoke = (Invoke) wrapper.node();
        return new MethodWrapper(invoke.getTargetMethod());
    }

    @NotNull
    public static String getConstantValue(@NotNull WrappedIRNode wrapper) {
        assert isConstant(wrapper);
        ConstantNode constant = (ConstantNode) wrapper.node();
        return constant.getValue().toValueString();
    }

    @NotNull
    public static String getLogicText(@NotNull WrappedIRNode node) {
        assert isLogic(node);
        LogicNode logic = (LogicNode) node.node();
        throw new RuntimeException("Not implemented");
    }
}
