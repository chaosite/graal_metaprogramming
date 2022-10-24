package il.ac.technion.cs.mipphd.graal.domains

import apron.*
import elina.OptPoly
import gmp.Mpq
import java.math.BigInteger

fun Scalar.toDouble(): Double = when (this) {
    is DoubleScalar -> get()
    is MpqScalar -> get().doubleValue()
    else -> throw ClassCastException("class $javaClass is unsupported")
}

fun Number.toMpq(): Mpq = when (this) {
    is Double -> Mpq(this)
    is Int -> Mpq(this)
    is BigInteger -> Mpq(this)
    else -> throw ClassCastException("Unsupported $javaClass")
}

fun Number.toScalar(): Scalar =
    MpqScalar(toMpq())

fun Mpq.toScalar(): Scalar = MpqScalar(this)

operator fun Mpq.plus(o: Mpq): Mpq = this.clone().apply { this.add(o) }

operator fun Mpq.times(o: Mpq): Mpq = this.clone().apply { this.mul(o) }

fun makeLinexpr0(coeffs: Array<out Mpq>, cst: Mpq): Linexpr0 = Linexpr0(coeffs.size).apply {
    for (i in coeffs.indices) {
        setCoeff(i, coeffs[i].toScalar())
    }
    this.cst = cst.toScalar()
}

fun makeLincons0(coeffs: Array<out Mpq>, cst: Mpq, cons: Int): Lincons0 =
    Lincons0(cons, makeLinexpr0(coeffs, cst))

data class Monom(val name: String, val coeff: Mpq)

data class SymbolicLinExpr(
    val monoms: List<Monom> = listOf(),
    val constant: Mpq = 0.toMpq()
) {
    fun toLinexpr0(state: PolyhedralAbstractState): Pair<PolyhedralAbstractState, Linexpr0> {
        var o = state
        for ((varname, _) in monoms)
            if (!o.isDeclared(varname))
                o = o.declareVariable(varname)
        val coeffs = Array<Mpq>(o.dimension) { 0.toMpq() }
        for ((varname, coeff) in monoms)
            coeffs[o.varIndex(varname)] = coeff

        return Pair(o, makeLinexpr0(coeffs, constant))
    }

    operator fun plus(o: SymbolicLinExpr): SymbolicLinExpr {
        val c = this.constant.clone()
        c.add(o.constant)
        return SymbolicLinExpr(
            (monoms + o.monoms)
                .groupBy(Monom::name)
                .map { it.value.reduce { a, b -> Monom(a.name, a.coeff + b.coeff) } }, c
        )
    }

    operator fun times(o: SymbolicLinExpr): SymbolicLinExpr {
        if (o.monoms.isNotEmpty())
            throw NotImplementedError() // TODO: Only handling linear cases, maybe do something better?
        return SymbolicLinExpr(monoms.map { Monom(it.name, it.coeff * o.constant) }, constant * o.constant)

    }
}

class PolyhedralAbstractState(
    private val abstract: Abstract0 = Abstract0(man, 0, 0),
    private val vars: List<String> = listOf()
) {
    companion object {
        val man = OptPoly(false)


        fun bottom() = PolyhedralAbstractState(Abstract0(man, 0, 0, true), listOf())
        fun top() = PolyhedralAbstractState(Abstract0(man, 0, 0, false), listOf())
    }

    init {
        val dim = abstract.getDimension(man)
        assert(dim.intDim == vars.size)
        assert(dim.realDim == 0)
    }

    val dimension: Int
        get() = vars.size

    fun declareVariable(name: String): PolyhedralAbstractState {
        if (name in vars)
            throw IllegalArgumentException("var already declared")
        return PolyhedralAbstractState(
            abstract.addDimensionsCopy(man, Dimchange(1, 0, intArrayOf(vars.size)), false),
            vars + name
        )
    }

    fun isUnconstrained(name: String): Boolean {
        if (!isDeclared(name))
            return true
        val (_, dim) = varToDim(name)
        return abstract.isDimensionUnconstrained(man, dim)
    }

    fun isUnconstrained(rhs: SymbolicLinExpr): Boolean =
        rhs.monoms.isEmpty() || rhs.monoms.any { isUnconstrained(it.name) }

    private fun assign(name: String, rhs: Linexpr0): PolyhedralAbstractState {
        val (o, dim) = varToDim(name)
        return PolyhedralAbstractState(
            o.abstract.assignCopy(man, dim, rhs, null),
            o.vars
        )
    }

    fun assign(name: String, rhs: Number): PolyhedralAbstractState {
        val (o, _) = varToDim(name)
        return o.assign(name, makeLinexpr0(Array(o.vars.size) { 0.toMpq() }, rhs.toMpq()))
    }


    fun assign(name: String, rhs: SymbolicLinExpr): PolyhedralAbstractState {
        val (o, _) = varToDim(name)
        val (o2, linexpr0) = rhs.toLinexpr0(o)
        return o2.assign(name, linexpr0)
    }

    fun assume(name: String, rel: String /* TODO: enum? */, constant: Number): PolyhedralAbstractState {

        val (o, dim) = varToDim(name)
        val row = Array<Mpq>(o.vars.size) { 0.toMpq() }
        var specConstant: Number = 0
        var specRel = 0
        if (rel in listOf(">", ">=", "=", "!=")) {
            row[dim] = 1.toMpq()
            specConstant = -constant.toDouble()
            specRel = when (rel) {
                ">" -> Lincons0.SUP
                ">=" -> Lincons0.SUPEQ
                "!=" -> Lincons0.DISEQ
                "=" -> Lincons0.EQ
                "==" -> Lincons0.EQ
                else -> throw RuntimeException("Unreachable, rel=${rel}")
            }
        } else if (rel in listOf("<", "<=")) {
            row[dim] = (-1).toMpq()
            specConstant = constant
            specRel = when (rel) {
                "<" -> Lincons0.SUP
                "<=" -> Lincons0.SUPEQ
                else -> throw RuntimeException("Unreachable, rel=${rel}")
            }
        }

        val constraint = makeLincons0(row, specConstant.toMpq(), specRel)
        return PolyhedralAbstractState(
            o.abstract.meetCopy(man, arrayOf(constraint)),
            o.vars
        )
    }

    fun join(o: PolyhedralAbstractState): PolyhedralAbstractState {
        if (this.abstract.isBottom(man) || o.abstract.isTop(man)) return o
        if (o.abstract.isBottom(man) || this.abstract.isTop(man)) return this

        if (vars != o.vars) {
            val o1 = conform(o)
            val o2 = o.conform(this)
            println("Conformed on join: $o1, $o2")
            return o1.join(o2)
        }

        return PolyhedralAbstractState(
            abstract.joinCopy(man, o.abstract),
            vars
        )
    }

    fun widen(o: PolyhedralAbstractState): PolyhedralAbstractState {
        if (this.abstract.isBottom(man) || o.abstract.isTop(man)) return o
        if (o.abstract.isBottom(man) || this.abstract.isTop(man)) return this

        return PolyhedralAbstractState(
            abstract.widening(man, o.abstract),
            vars
        )
    }

    fun conform(o: PolyhedralAbstractState): PolyhedralAbstractState {
        if (vars == o.vars)
            return this
        val newVars = (vars + o.vars).sorted().distinct()
        val newState = newVars.fold(PolyhedralAbstractState()) { acc, v -> acc.declareVariable(v) }
        val lincons = abstract.toLincons(man).map {
            Lincons0(
                it.kind,
                SymbolicLinExpr(
                    (0 until it.expression.size).map { i ->
                        Monom(vars[i], (it.expression.coeffs[i] as Scalar).toDouble().toMpq())
                    }, (it.expression.cst as Scalar).toDouble().toMpq()
                )
                    .toLinexpr0(newState).second
            )
        }

        return PolyhedralAbstractState(newState.abstract.meetCopy(man, lincons.toTypedArray()), newVars)
    }

    fun isTop() = abstract.isTop(man)

    fun isBottom() = abstract.isBottom(man)

    fun isDeclared(name: String): Boolean = name in vars

    fun varIndex(varname: String) = vars.indexOf(varname)

    fun contains() {

    }

    override fun toString() = when {
        isTop() -> "PolyhedralAbstractState.top()"
        isBottom() -> "PolyhedralAbstractState.bottom()"
        else -> "PolyhedralAbstractState(${abstract.toString(man)}, ${vars})"
    }

    private fun varToDim(name: String): Pair<PolyhedralAbstractState, Int> {
        val o = if (name in vars) this else declareVariable(name)
        return Pair(o, o.varIndex(name))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PolyhedralAbstractState

        if (vars != other.vars) return false
        if (!abstract.isEqual(man, other.abstract)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = abstract.hashCode(man)
        result = 31 * result + vars.hashCode()
        return result
    }
}