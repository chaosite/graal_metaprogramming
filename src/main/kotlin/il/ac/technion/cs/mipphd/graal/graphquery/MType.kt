package il.ac.technion.cs.mipphd.graal.graphquery

sealed class MType {
    open fun match(other: MType) = other === this
}

object MString : MType()
object MInteger : MType()
object MBoolean : MType()

data class MFunction(val parameters: List<MType>, val returnType: MType) : MType() {
    override fun match(other: MType): Boolean {
        if (other !is MFunction)
            return false
        return parameters.zip(other.parameters).all { it.first.match(it.second) } && returnType.match(other.returnType)
    }
}

data class MStruct(val contents: Map<String, MType>) : MType() {
    override fun match(other: MType): Boolean {
        if (other !is MStruct)
            return false
        return contents.size == other.contents.size && contents.all { it.key in other.contents && it.value == other.contents[it.key] }
    }
}