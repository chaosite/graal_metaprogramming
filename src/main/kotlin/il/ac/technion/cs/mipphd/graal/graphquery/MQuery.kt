package il.ac.technion.cs.mipphd.graal.graphquery

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.parser.Parser
import com.github.h0tk3y.betterParse.utils.Tuple2
import il.ac.technion.cs.mipphd.graal.utils.EdgeWrapper
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper

sealed class QueryTarget
data class QueryTargetNode(val node: NodeWrapper) : QueryTarget()
data class QueryTargetEdge(val source: NodeWrapper, val edge: EdgeWrapper) : QueryTarget()

sealed class MQuery {
    fun interpret(target: QueryTarget): Boolean {
        typecheck(target) || return false // TODO: Do this during parse
        val value = value(target)
        if (value !is BooleanValue)
            throw RuntimeException("?!?")
        return value.value
    }

    abstract fun typecheck(target: QueryTarget): Boolean
    abstract fun type(target: QueryTarget): MType
    abstract fun value(target: QueryTarget): MValue
    abstract fun serialize(): String
}

sealed class MValue : MQuery() {
    abstract val value: Any
    override fun typecheck(target: QueryTarget) = true
    override fun value(target: QueryTarget) = this
}

data class Variable(val name: String) : MQuery() {
    override fun type(target: QueryTarget) = predefinedVariable.type
    override fun value(target: QueryTarget) = predefinedVariable.contents
    override fun typecheck(target: QueryTarget) = true
    override fun serialize() = name

    private val predefinedVariable get() = predefined.getValue(name)
}

data class BooleanValue(override val value: Boolean) : MValue() {
    override fun type(target: QueryTarget) = MBoolean
    override fun serialize() = value.toString()
}

data class StringValue(override val value: String) : MValue() {
    override fun type(target: QueryTarget) = MString
    override fun serialize() = """'$value'"""
}

data class IntegerValue(override val value: Long) : MValue() {
    override fun type(target: QueryTarget) = MInteger
    override fun serialize() = value.toString()
}

data class StructValue(override val value: Map<String, MValue>) : MValue() {
    override fun type(target: QueryTarget): MType = MStruct(value.mapValues { it.value.type(target) })
    override fun serialize() = TODO("Not implemented")
}

data class FunctionValue(val type: MType, override val value: (List<MQuery>, QueryTarget) -> MValue) : MValue() {
    override fun type(target: QueryTarget) = type
    override fun serialize() = TODO("Not implemented")
}

data class Equals(val lvalue: MQuery, val rvalue: MQuery) : MQuery() {
    override fun type(target: QueryTarget) = MBoolean
    override fun typecheck(target: QueryTarget) = lvalue.typecheck(target) &&
            rvalue.typecheck(target) &&
            lvalue.type(target).match(rvalue.type(target))

    override fun value(target: QueryTarget): MValue {
        return BooleanValue(lvalue.value(target) == rvalue.value(target))
    }

    override fun serialize() = "(${lvalue.serialize()}) = (${rvalue.serialize()})"
}

data class And(val left: MQuery, val right: MQuery) : MQuery() {
    override fun type(target: QueryTarget) = MBoolean
    override fun typecheck(target: QueryTarget) = left.typecheck(target) &&
            right.typecheck(target) &&
            left.type(target).match(MBoolean) &&
            right.type(target).match(MBoolean)

    override fun value(target: QueryTarget): MValue {
        return BooleanValue(left.value(target).value as Boolean && right.value(target).value as Boolean)
    }

    override fun serialize() = "(${left.serialize()}) and (${right.serialize()})"
}

data class Or(val left: MQuery, val right: MQuery) : MQuery() {
    override fun type(target: QueryTarget) = MBoolean
    override fun typecheck(target: QueryTarget) = left.typecheck(target) &&
            right.typecheck(target) &&
            left.type(target).match(MBoolean) &&
            right.type(target).match(MBoolean)

    override fun value(target: QueryTarget) = BooleanValue(
        left.value(target).value as Boolean || right.value(target).value as Boolean
    )

    override fun serialize() = "(${left.serialize()}) or (${right.serialize()})"
}

data class Not(val query: MQuery) : MQuery() {
    override fun type(target: QueryTarget) = MBoolean
    override fun typecheck(target: QueryTarget) = query.typecheck(target) &&
            query.type(target).match(MBoolean)

    override fun value(target: QueryTarget) = BooleanValue(!(query.value(target).value as Boolean))

    override fun serialize() = "not (${query.serialize()})"
}

sealed class MetadataOption {
    object Kleene : MetadataOption() {
        override fun serialize() = "*"
    }

    object Repeated : MetadataOption() {
        override fun serialize() = "[]"
    }

    data class CaptureName(val name: String) : MetadataOption() {
        override fun serialize() = "(?P<$name>)"
    }

    abstract fun serialize(): String
}

data class Metadata(val query: MQuery, val options: List<MetadataOption> = listOf()) : MQuery() {
    override fun type(target: QueryTarget) = query.type(target)
    override fun typecheck(target: QueryTarget) = query.typecheck(target)
    override fun value(target: QueryTarget) = query.value(target)
    override fun serialize() =
        (if (options.isNotEmpty()) "${options.joinToString { it.serialize() }}|" else "") + query.serialize()
}

data class FuncCall(val func: MQuery, val parameters: List<MQuery>) : MQuery() {
    override fun typecheck(target: QueryTarget): Boolean {
        val funcType = func.type(target)
        return parameters.all { it.typecheck(target) } &&
                funcType is MFunction &&
                parameters.size == funcType.parameters.size &&
                funcType.parameters.zip(parameters).all { (t, q) -> t.match(q.type(target)) }
    }

    override fun type(target: QueryTarget) = (func.type(target) as MFunction).returnType

    override fun value(target: QueryTarget) = (func.value(target) as FunctionValue).value(parameters, target)

    override fun serialize() = "${func.serialize()}(${parameters.joinToString(",", transform = MQuery::serialize)})"
}

data class Access(val base: MQuery, val accessor: MQuery) : MQuery() {
    override fun typecheck(target: QueryTarget): Boolean {
        val baseType = base.type(target)
        return base.typecheck(target) &&
                accessor.typecheck(target) &&
                baseType is MStruct &&
                accessor is Variable &&
                accessor.name in baseType.contents
    }

    override fun type(target: QueryTarget): MType {
        val baseType = base.type(target)
        if (baseType !is MStruct || accessor !is Variable) {
            throw RuntimeException("Type error, b: $base r: $accessor")
        }
        return baseType.contents.getValue(accessor.name)
    }

    override fun value(target: QueryTarget): MValue {
        val baseValue = base.value(target)
        if (baseValue !is StructValue || accessor !is Variable) {
            throw java.lang.RuntimeException("Type error, b: $base r: $accessor")
        }
        return baseValue.value.getValue(accessor.name)
    }

    override fun serialize() = "(${base.serialize()}.${accessor.serialize()})"
}

val mGrammar = object : Grammar<MQuery>() {
    val str by regexToken("""["][^"]+["]|['][^']+[']""")
    val int by regexToken("""-?[0-9]+""")
    val lpar by literalToken("(")
    val rpar by literalToken(")")
    val lbra by literalToken("[")
    val rbra by literalToken("]")
    val comma by literalToken(",")
    val dot by literalToken(".")
    val not by literalToken("not")
    val and by literalToken("and")
    val or by literalToken("or")
    val eq by literalToken("=")
    val id by regexToken("""\w+""")
    val kleeneStar by literalToken("*")
    val pipe by literalToken("|")
    val question by literalToken("?")
    val langle by literalToken("<")
    val rangle by literalToken(">")
    val _ws by regexToken("""\s+""", ignore = true)

    val negation by -not * parser(this::value) map { Not(it) }
    val parenExpression by -lpar * parser(this::orChain) * -rpar
    val literal: Parser<MQuery> by (
            id map { Variable(it.text) }) or
            (int map { IntegerValue(it.text.toLong()) }) or
            (str map { StringValue(it.text.removeSurrounding("\"").removeSurrounding("'")) }) or
            negation or
            parenExpression
    val nonLeftRecursive: Parser<MQuery> by (
            literal * parser(this::valueTag) map { it.t2(it.t1) })

    val valueTag: Parser<(MQuery) -> MQuery> by (
            -lpar * separated(parser(this::value), comma, acceptZero = true) * -rpar * optional(parser(this::valueTag))
                    map { outer ->
                { inner: MQuery ->
                    val f = FuncCall(inner, outer.t1.terms)
                    outer.t2?.invoke(f) ?: f
                }
            }) or
            (-dot * (id map { Variable(it.text) }) * optional(parser(this::valueTag)) map { outer ->
                { inner: MQuery ->
                    val f = Access(inner, outer.t1)
                    outer.t2?.invoke(f) ?: f
                }
            }) or
            (-eq * parser(this::value) map { outer -> { inner -> Equals(inner, outer) } })
    val value: Parser<MQuery> by negation or nonLeftRecursive or literal

    val andChain by leftAssociative(value, and) { l, _, r -> And(l, r) }
    val orChain by leftAssociative(andChain, or) { l, _, r -> Or(l, r) }

    val option: Parser<MetadataOption> by (
            kleeneStar asJust MetadataOption.Kleene) or (
            (-lpar * -question * id * -langle * id * -rangle * -rpar) map {
                when (it.t1.text) {
                    "P" -> MetadataOption.CaptureName(it.t2.text)
                    else -> throw RuntimeException("Unexpected option character '${it.t1.text}'")
                }
            }) or (
            (lbra * rbra) asJust MetadataOption.Repeated)

    val metadata: Parser<MQuery> by (
            oneOrMore(option) * -pipe * orChain map { Metadata(it.t2, it.t1) }) or
            (orChain map { Metadata(it) })

    override val rootParser by metadata
}

fun parseMQuery(input: String): MQuery = mGrammar.parseToEnd(input)