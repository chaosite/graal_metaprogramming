package il.ac.technion.cs.mipphd.graal.graphquery.datalog

interface Element {
    fun render(builder: StringBuilder, indent: String)
}

@DslMarker
annotation class SouffleMarker

@SouffleMarker
abstract class SouffleClause(val seperator: String, val prefix: String = "(", val postfix: String = ")") : Element {
    val children = arrayListOf<Element>()
    protected fun <T : Element> initClause(clause: T, init: T.() -> Unit): T {
        clause.init()
        children.add(clause)
        return clause
    }

    override fun render(builder: StringBuilder, indent: String) {
        if (prefix.isNotEmpty())
            builder.append("$indent$prefix\n")
        for ((i, c) in children.withIndex()) {
            c.render(builder, "$indent  ")
            if (i != children.size - 1) {
                builder.append(seperator)
                builder.append("\n")
            } else if (postfix.isNotEmpty()) {
                builder.append("\n")
            }

        }
        if (postfix.isNotEmpty())
            builder.append("$indent$postfix\n")
    }

    override fun toString(): String {
        val builder = StringBuilder()
        render(builder, "")
        return builder.toString()
    }

    fun and(init: SouffleAnd.() -> Unit) = initClause(SouffleAnd(), init)
    fun or(init: SouffleOr.() -> Unit) = initClause(SouffleOr(), init)
    fun relation(relation: String, init: SouffleRelationCall.() -> Unit) = initClause(SouffleRelationCall(relation), init)
    fun expression(expr: String, init: SouffleExpression.() -> Unit = {}) = initClause(SouffleExpression(expr), init)
}

class SouffleRelation(val name: String, val params: List<String>) : SouffleClause(",", prefix = "", postfix = "") {
    override fun render(builder: StringBuilder, indent: String) {
        builder.append(name)
        builder.append('(')
        builder.append(params.joinToString(separator = ", "))
        builder.append(")")
        if (children.isNotEmpty())
            builder.append(" :-\n")
        super.render(builder, indent)
        builder.append(".")
    }
}

class SouffleAnd() : SouffleClause(",")
class SouffleOr() : SouffleClause(";")

class SouffleRelationCall(private val relation: String) : Element {
    val params = arrayListOf<Element>()
    override fun render(builder: StringBuilder, indent: String) {
        builder.append(indent)
        builder.append(relation)
        builder.append("(")
        for ((i, param) in params.withIndex()) {
            param.render(builder, "")
            if (i != params.size - 1)
                builder.append(", ")
        }
        builder.append(")")
    }

    fun param(expr: String, init: SouffleExpression.() -> Unit = {}) {
        val p = SouffleExpression(expr)
        p.init()
        params.add(p)
    }
}

class SouffleExpression(private val text: String) : Element {
    override fun render(builder: StringBuilder, indent: String) {
        //builder.append(indent)
        builder.append(text)
        //builder.append("\n")
    }
}

fun souffle(name: String, params: List<String>, init: SouffleRelation.() -> Unit): SouffleRelation {
    val s = SouffleRelation(name, params)
    s.init()
    return s
}