package il.ac.technion.cs.mipphd.graal.utils;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.MetaUtil;

public class SourcePosTool {
    public static int getBCI(ValueNode node) {
        return node.getNodeSourcePosition().getBCI();
    }

    public static String getLocation(ValueNode node) {
        return MetaUtil.toLocation(getMethod(node), getBCI(node));
    }

    public static ResolvedJavaMethod getMethod(ValueNode node) {
        return node.getNodeSourcePosition().getMethod();
    }

    public static StackTraceElement getStackTraceElement(ValueNode node) {
        return getMethod(node).asStackTraceElement(getBCI(node));
    }
}
