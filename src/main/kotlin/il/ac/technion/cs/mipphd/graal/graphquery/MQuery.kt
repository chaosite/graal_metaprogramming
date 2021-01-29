package il.ac.technion.cs.mipphd.graal.graphquery

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.parser.Parser
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper


sealed class MQuery {
    fun interpret(node: NodeWrapper): Boolean {
        typecheck(node) || return false // TODO: Do this during parse
        val value = value(node)
        if (value !is BooleanValue)
            throw RuntimeException("?!?")
        return value.value
    }

    abstract fun typecheck(node: NodeWrapper): Boolean
    abstract fun type(node: NodeWrapper): MType
    abstract fun value(node: NodeWrapper): MValue
    abstract fun serialize(): String
}

sealed class MValue : MQuery() {
    abstract val value: Any
    override fun typecheck(node: NodeWrapper) = true
    override fun value(node: NodeWrapper) = this
}

data class Variable(val name: String) : MQuery() {
    override fun type(node: NodeWrapper) = predefinedVariable.type
    override fun value(node: NodeWrapper) = predefinedVariable.contents
    override fun typecheck(node: NodeWrapper) = true
    override fun serialize() = name

    private val predefinedVariable get() = predefined.getValue(name)
}

data class BooleanValue(override val value: Boolean) : MValue() {
    override fun type(node: NodeWrapper) = MBoolean
    override fun serialize() = value.toString()
}

data class StringValue(override val value: String) : MValue() {
    override fun type(node: NodeWrapper) = MString
    override fun serialize() = """"$value""""
}

data class IntegerValue(override val value: Long) : MValue() {
    override fun type(node: NodeWrapper) = MInteger
    override fun serialize() = value.toString()
}

data class StructValue(override val value: Map<String, MValue>) : MValue() {
    override fun type(node: NodeWrapper): MType = MStruct(value.mapValues { it.value.type(node) })
    override fun serialize() = TODO("Not implemented")
}

data class FunctionValue(val type: MType, override val value: (List<MQuery>, NodeWrapper) -> MValue) : MValue() {
    override fun type(node: NodeWrapper) = type
    override fun serialize() = TODO("Not implemented")
}

data class Equals(val lvalue: MQuery, val rvalue: MQuery) : MQuery() {
    override fun type(node: NodeWrapper) = MBoolean
    override fun typecheck(node: NodeWrapper) = lvalue.typecheck(node) &&
            rvalue.typecheck(node) &&
            lvalue.type(node).match(rvalue.type(node))

    override fun value(node: NodeWrapper): MValue {
        return BooleanValue(lvalue.value(node) == rvalue.value(node))
    }

    override fun serialize() = "(${lvalue.serialize()}) = (${rvalue.serialize()})"
}

data class And(val left: MQuery, val right: MQuery) : MQuery() {
    override fun type(node: NodeWrapper) = MBoolean
    override fun typecheck(node: NodeWrapper) = left.typecheck(node) &&
            right.typecheck(node) &&
            left.type(node).match(MBoolean) &&
            right.type(node).match(MBoolean)

    override fun value(node: NodeWrapper): MValue {
        return BooleanValue(left.value(node).value as Boolean && right.value(node).value as Boolean)
    }

    override fun serialize() = "(${left.serialize()}) and (${right.serialize()})"
}

data class Or(val left: MQuery, val right: MQuery) : MQuery() {
    override fun type(node: NodeWrapper) = MBoolean
    override fun typecheck(node: NodeWrapper) = left.typecheck(node) &&
            right.typecheck(node) &&
            left.type(node).match(MBoolean) &&
            right.type(node).match(MBoolean)

    override fun value(node: NodeWrapper) = BooleanValue(
        left.value(node).value as Boolean && right.value(node).value as Boolean
    )

    override fun serialize() = "(${left.serialize()}) or (${right.serialize()})"
}

data class Not(val query: MQuery) : MQuery() {
    override fun type(node: NodeWrapper) = MBoolean
    override fun typecheck(node: NodeWrapper) = query.typecheck(node) &&
            query.type(node).match(MBoolean)

    override fun value(node: NodeWrapper) = BooleanValue(!(query.value(node).value as Boolean))

    override fun serialize() = "not (${query.serialize()})"
}

data class FuncCall(val func: MQuery, val parameters: List<MQuery>) : MQuery() {
    override fun typecheck(node: NodeWrapper): Boolean {
        val funcType = func.type(node)
        return parameters.all { it.typecheck(node) } &&
                funcType is MFunction &&
                parameters.size == funcType.parameters.size &&
                funcType.parameters.zip(parameters).all { (t, q) -> t.match(q.type(node)) }
    }

    override fun type(node: NodeWrapper) = (func.type(node) as MFunction).returnType

    override fun value(node: NodeWrapper) = (func.value(node) as FunctionValue).value(parameters, node)

    override fun serialize() = "(${func.serialize()})(${parameters.joinToString(",", transform = MQuery::serialize)})"
}

data class Access(val base: MQuery, val accessor: MQuery) : MQuery() {
    override fun typecheck(node: NodeWrapper): Boolean {
        val baseType = base.type(node)
        return base.typecheck(node) &&
                accessor.typecheck(node) &&
                baseType is MStruct &&
                accessor is Variable &&
                accessor.name in baseType.contents
    }

    override fun type(node: NodeWrapper): MType {
        val baseType = base.type(node)
        if (baseType !is MStruct || accessor !is Variable) {
            throw RuntimeException("Type error, b: $base r: $accessor")
        }
        return baseType.contents.getValue(accessor.name)
    }

    override fun value(node: NodeWrapper): MValue {
        val baseValue = base.value(node)
        if (baseValue !is StructValue || accessor !is Variable) {
            throw java.lang.RuntimeException("Type error, b: $base r: $accessor")
        }
        return baseValue.value.getValue(accessor.name)
    }

    override fun serialize() = "(${base.serialize()}).(${accessor.serialize()})"
}

val mGrammar = object : Grammar<MQuery>() {
    val str by regexToken("""["][^"]+["]|['][^']+[']""")
    val int by regexToken("""-?[0-9]+""")
    val lpar by literalToken("(")
    val rpar by literalToken(")")
    val comma by literalToken(",")
    val dot by literalToken(".")
    val not by literalToken("not")
    val and by literalToken("and")
    val or by literalToken("or")
    val eq by literalToken("=")
    val id by regexToken("""\w+""")
    val ws by regexToken("""\s+""", ignore = true)

    val negation by -not * parser(this::value) map { Not(it) }
    val parenExpression by -lpar * parser(this::orChain) * -rpar
    val literal: Parser<MQuery> by (
            id map { Variable(it.text)}) or
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
            (-eq * parser(this::value) map { outer -> { inner -> Equals(inner, outer)}})
    val value: Parser<MQuery> by negation or parenExpression or nonLeftRecursive or literal

    val andChain by leftAssociative(value, and) { l, _, r -> And(l, r) }
    val orChain by leftAssociative(andChain, or) { l, _, r -> Or(l, r) }

    override val rootParser by orChain
}

fun parseMQuery(input: String): MQuery = mGrammar.parseToEnd(input)