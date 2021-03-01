package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.utils.NodeWrapperUtils
import java.lang.RuntimeException


data class PredefinedVariable(val name: String, val type: MType, val contents: MValue)

private fun createMap(vararg l: PredefinedVariable) = l.map { Pair(it.name, it) }.toMap()
private fun functionVariable(name: String, type: MType, impl: (List<MQuery>, QueryTarget) -> MValue) =
    PredefinedVariable(name, type, FunctionValue(type, impl))

val predefined = createMap(
    functionVariable("is", MFunction(listOf(MString), MBoolean)) { p, t ->
        val cmp = (p[0] as StringValue).value
        when (t) {
            is QueryTargetNode -> BooleanValue(t.node.isType(cmp))
            is QueryTargetEdge -> BooleanValue(t.edge.label == cmp)
        }
    },
    functionVariable(
        "method",
        MFunction(listOf(), MStruct(mapOf(Pair("className", MString), Pair("name", MString))))
    ) { _, t ->
        if (t is QueryTargetNode) {
            val method = NodeWrapperUtils.getTargetMethod(t.node)

            StructValue(
                mapOf(
                    Pair("className", StringValue(method.declaringClassName)),
                    Pair("name", StringValue(method.name))
                )
            )
        } else {
            throw RuntimeException("Not a node")
        }
    },
    functionVariable("name", MFunction(listOf(), MString)) { _, t ->
        when (t) {
            is QueryTargetNode -> TODO("Implement name for nodes?")
            is QueryTargetEdge -> StringValue(t.edge.name)
        }
    },
    PredefinedVariable("five", MInteger, IntegerValue(5)) // for debug
)