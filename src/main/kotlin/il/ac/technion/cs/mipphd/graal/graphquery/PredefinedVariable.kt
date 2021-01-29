package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapperUtils


data class PredefinedVariable(val name: String, val type: MType, val contents: MValue)

private fun createMap(vararg l: PredefinedVariable) = l.map { Pair(it.name, it) }.toMap()
private fun functionVariable(name: String, type: MType, impl: (List<MQuery>, NodeWrapper) -> MValue) =
    PredefinedVariable(name, type, FunctionValue(type, impl))

val predefined = createMap(
    functionVariable("is", MFunction(listOf(MString), MBoolean)) { p, n ->
        val nodeType = (p[0] as StringValue).value
        BooleanValue(n.isType(nodeType))
    },
    functionVariable("method", MFunction(listOf(), MStruct(mapOf(Pair("className", MString), Pair("name", MString))))) { p, n ->
        val method = NodeWrapperUtils.getTargetMethod(n)

        StructValue(mapOf(
            Pair("className", StringValue(method.declaringClassName)),
            Pair("name", StringValue(method.name))
        ))
    },
    PredefinedVariable("five", MInteger, IntegerValue(5)) // for debug
)