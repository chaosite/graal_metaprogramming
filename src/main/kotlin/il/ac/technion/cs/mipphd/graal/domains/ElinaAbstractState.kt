package il.ac.technion.cs.mipphd.graal.domains

import apron.*
import elina.OptOctagon
import elina.OptPoly
import gmp.Mpq
import java.math.BigInteger

fun Scalar.toDouble(): Double = when (this) {
    is DoubleScalar -> get()
    is MpqScalar -> get().doubleValue()
    else -> throw ClassCastException("class $javaClass is unsupported")
}

fun Scalar.toMpq(): Mpq = when (this) {
    is DoubleScalar -> get().toMpq()
    is MpqScalar -> get()
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

fun makeLincons0(linexpr0: Linexpr0, cons: Int): Lincons0 =
    Lincons0(cons, linexpr0)

data class Monom(val name: String, val coeff: Mpq = 1.toMpq()) {
    constructor(name: String, coeff: Number) : this(name, coeff.toMpq())
}

data class SymbolicLinExpr(
    val monoms: List<Monom> = listOf(),
    val constant: Mpq = 0.toMpq()
) {
    companion object {
        val NEGATIVE_ONE = SymbolicLinExpr(constant = (-1).toMpq())
        val ZERO = SymbolicLinExpr(constant = 0.toMpq())
        val ONE = SymbolicLinExpr(constant = 1.toMpq())
    }
    constructor(c: Mpq, vararg ms: Monom) : this(ms.asList(), c)
    constructor(c: Number, vararg ms: Monom) : this(c.toMpq(), *ms)

    fun toLinexpr0(state: ElinaAbstractState): Pair<ElinaAbstractState, Linexpr0> {
        var o = state
        for ((varName, _) in monoms)
            if (!o.isDeclared(varName))
                o = o.declareVariable(varName)
        val coeffs = Array<Mpq>(o.dimension) { 0.toMpq() }
        for ((varName, coeff) in monoms)
            coeffs[o.varIndex(varName)] = coeff

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

    fun negate(): SymbolicLinExpr {
        return SymbolicLinExpr(
            monoms.map { Monom(it.name, it.coeff.clone().apply { neg() }) },
            constant.clone().apply { neg() }
        )
    }

    fun toShortString() = if (monoms.isEmpty() && constant == 0.toMpq()) "0" else
        (monoms.map { "${it.coeff}*${it.name}" } + if (constant != 0.toMpq()) listOf("$constant") else listOf())
            .joinToString(" + ")
}

enum class RelOp(val strReps: Collection<String>, val elinaRep: Int, val negate: Boolean = false) {
    GT(">", Lincons0.SUP),
    GE(">=", Lincons0.SUPEQ),
    NE("!=", Lincons0.DISEQ),
    EQ(listOf("=", "=="), Lincons0.EQ),
    LT("<", Lincons0.SUP, true),
    LE("<=", Lincons0.SUPEQ, true);

    companion object {
        private val REL_MAP: Map<String, RelOp>
        val STRINGS: List<String> = RelOp.values().flatMap { it.strReps }

        init {
            val map = HashMap<String, RelOp>()
            for (op in RelOp.values()) {
                for (rep in op.strReps) {
                    map[rep] = op
                }
            }
            REL_MAP = map;
        }

        fun fromStrRep(rep: String): RelOp = REL_MAP[rep] ?: throw IllegalArgumentException()
    }
    constructor(strRep: String, elinaRep: Int, negate: Boolean = false) : this(listOf(strRep), elinaRep, negate)
}

data class SymbolicLinConstraint private constructor(
    val expr: SymbolicLinExpr,
    val rel: RelOp
) {
    companion object {
        fun fromRelExpr(exprA: SymbolicLinExpr, rel: RelOp, exprB: SymbolicLinExpr): SymbolicLinConstraint =
            SymbolicLinConstraint(exprA.plus(
            if (rel.negate) exprB.negate() else exprB), rel)
    }

    fun toLincons0(state: ElinaAbstractState): Lincons0 {
        val (o, linexpr0) = expr.toLinexpr0(state)
        return makeLincons0(linexpr0, rel.elinaRep)
    }

    fun negated() = SymbolicLinConstraint(expr, when(rel) {
        RelOp.GT -> RelOp.LE
        RelOp.GE -> RelOp.LT
        RelOp.EQ -> RelOp.NE
        RelOp.NE -> RelOp.EQ
        RelOp.LT -> RelOp.GE
        RelOp.LE -> RelOp.GT
    })

    fun toShortString() = "${expr.toShortString()} ${rel.strReps.first()} 0"
}


abstract class ElinaAbstractState(
    private val man: Manager,
    private val abstract: Abstract0 = Abstract0(man, 0, 0),
    private val vars: List<String> = listOf()
) {

    init {
        val dim = abstract.getDimension(man)
        assert(dim.intDim == vars.size)
        assert(dim.realDim == 0)
    }

    val dimension: Int
        get() = vars.size

    protected abstract fun construct(
        abstract: Abstract0 = Abstract0(man, 0, 0),
        vars: List<String> = listOf()
    ): ElinaAbstractState

    protected abstract fun top(): ElinaAbstractState
    protected abstract fun bottom(): ElinaAbstractState

    fun declareVariable(name: String): ElinaAbstractState {
        if (name in vars)
            throw IllegalArgumentException("var already declared")
        return construct(
            abstract.addDimensionsCopy(man, Dimchange(1, 0, intArrayOf(vars.size)), false),
            vars + name,
        )
    }

    private fun assign(name: String, rhs: Linexpr0): ElinaAbstractState {
        val (o, dim) = varToDim(name)
        return construct(
            o.abstract.assignCopy(man, dim, rhs, null),
            o.vars,
        )
    }

    fun assign(name: String, rhs: Mpq): ElinaAbstractState {
        val (o, _) = varToDim(name)
        return o.assign(name, makeLinexpr0(Array(o.vars.size) { 0.toMpq() }, rhs))
    }

    fun assign(name: String, rhs: Number) = assign(name, rhs.toMpq())

    fun assign(name: String, rhs: SymbolicLinExpr): ElinaAbstractState {
        val (o, _) = varToDim(name)
        val (o2, linexpr0) = rhs.toLinexpr0(o)
        return o2.assign(name, linexpr0)
    }

    fun substitute(name: String, rhs: Linexpr0): ElinaAbstractState {
        val (o, dim) = varToDim(name)
        return construct(o.abstract.substituteCopy(man, dim, rhs, null), o.vars)
    }

    fun substitute(name: String, rhs: SymbolicLinExpr): ElinaAbstractState {
        val (o, _) = varToDim(name)
        val (o2, linexpr0) = rhs.toLinexpr0(o)
        return o2.substitute(name, linexpr0)
    }

    fun substitute(name: String, rhs: Mpq): ElinaAbstractState {
        val (o, _) = varToDim(name)
        return o.substitute(name, makeLinexpr0(Array(o.vars.size) { 0.toMpq() }, rhs))
    }

    fun substitute(name: String, rhs: Number): ElinaAbstractState = assign(name, rhs.toMpq())

    fun assume(name: String, rel: String /* TODO: enum? */, constant: Mpq): ElinaAbstractState {
        val (o, dim) = varToDim(name)
        val row = Array<Mpq>(o.vars.size) { 0.toMpq() }
        var specConstant: Mpq = 0.toMpq()
        var specRel = 0
        if (rel in listOf(">", ">=", "=", "==", "!=")) {
            row[dim] = 1.toMpq()
            specConstant = constant.clone().apply { neg() }
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

        val constraint = makeLincons0(row, specConstant, specRel)
        return construct(
            o.abstract.meetCopy(man, arrayOf(constraint)),
            o.vars,
        )
    }

    fun assume(name: String, rel: String, constant: Number) = assume(name, rel, constant.toMpq())

    fun join(o: ElinaAbstractState): ElinaAbstractState {
        if (this.abstract.isBottom(man) || o.abstract.isTop(man)) return o
        if (o.abstract.isBottom(man) || this.abstract.isTop(man)) return this

        if (vars != o.vars) {
            val o1 = conform(o)
            val o2 = o.conform(this)
            return o1.join(o2)
        }

        return construct(abstract.joinCopy(man, o.abstract), vars)
    }

    fun meet(o: ElinaAbstractState): ElinaAbstractState {
        if (this.abstract.isBottom(man) || o.abstract.isTop(man)) return o
        if (o.abstract.isBottom(man) || this.abstract.isTop(man)) return this

        if (vars != o.vars) {
            val o1 = conform(o)
            val o2 = o.conform(this)
            return o1.meet(o2)
        }

        return construct(abstract.meetCopy(man, o.abstract), vars)
    }

    fun widen(o: ElinaAbstractState): ElinaAbstractState {
        if (this.abstract.isBottom(man) || o.abstract.isTop(man)) return o
        if (o.abstract.isBottom(man) || this.abstract.isTop(man)) return this

        if (vars != o.vars) {
            val o1 = conform(o)
            val o2 = o.conform(this)
            return o1.widen(o2)
        }

        return construct(
            abstract.widening(man, o.abstract),
            vars,
        )
    }

    fun forget(name: String): ElinaAbstractState {
        if (this.isBottom() || this.isTop()) return this

        if (name !in vars)
            throw IllegalArgumentException("$name not in $this")

        val (_, dim) = varToDim(name)

        return construct(
            abstract.forgetCopy(man, dim, true),
            vars,
        )
    }

    fun forgetByFilter(f: (String) -> Boolean): ElinaAbstractState =
        vars.filter(f).fold(this) { o, name -> o.forget(name) }

    fun conform(o: ElinaAbstractState): ElinaAbstractState {
        if (vars == o.vars)
            return this
        val newVars = (vars + o.vars).sorted().distinct()
        val newState = newVars.fold(construct()) { acc, v -> acc.declareVariable(v) } // TODO: Be specific wrt top
        val lincons = abstract.toLincons(man).map {
            Lincons0(
                it.kind,
                SymbolicLinExpr(
                    (0 until it.expression.size).map { i ->
                        Monom(vars[i], (it.expression.coeffs[i] as Scalar).toMpq())
                    }, (it.expression.cst as Scalar).toMpq()
                )
                    .toLinexpr0(newState).second
            )
        }

        return construct(newState.abstract.meetCopy(man, lincons.toTypedArray()), newVars)
    }

    fun getBound(name: String): Pair<Mpq, Mpq> {
        if (name !in vars)
            throw IllegalArgumentException("$name does not exist")

        val (_, dim) = varToDim(name)

        val interval = abstract.getBound(man, dim)
        // println(interval)
        return interval.run { inf.toMpq() to sup.toMpq() }
    }

    val coeffs: List<Mpq>
        get() {
            if (isTop() || isBottom())
                return listOf()
            return abstract.toLincons(man).flatMap { lincons -> lincons.coeffs.asIterable() }.map {
                when (it) {
                    is Scalar -> it.toMpq()
                    else -> throw (NotImplementedError("Didn't plan for: $it"))
                }
            }
        }

    fun isTop() = abstract.isTop(man)

    fun isBottom() = abstract.isBottom(man)

    fun isDeclared(name: String): Boolean = name in vars

    fun varIndex(varName: String) = vars.indexOf(varName)

    override fun toString() = when {
        isTop() -> "${this.javaClass.simpleName}.top()"
        isBottom() -> "${this.javaClass.simpleName}.bottom()"
        else -> "${this.javaClass.simpleName}(${abstract.toString(man, vars.toTypedArray())}, ${vars})"
    }

    private fun varToDim(name: String): Pair<ElinaAbstractState, Int> {
        val o = if (name in vars) this else declareVariable(name)
        return Pair(o, o.varIndex(name))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ElinaAbstractState

        if (vars != other.vars) return false
        if (!abstract.isEqual(man, other.abstract)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = abstract.hashCode(man)
        result = 31 * result + vars.hashCode()
        return result
    }

    fun testSanity() {
        val dim = abstract.getDimension(man)
        assert(dim.intDim == vars.size)
        assert(dim.realDim == 0)
    }
}

class OctagonElinaAbstractState(abstract: Abstract0, vars: List<String>) :
    ElinaAbstractState(man, abstract, vars) {
    companion object {
        val man = OptOctagon()

        fun bottom() = OctagonElinaAbstractState(Abstract0(man, 0, 0, true), listOf())
        fun top() = OctagonElinaAbstractState(Abstract0(man, 0, 0, false), listOf())
    }

    override fun construct(abstract: Abstract0, vars: List<String>): ElinaAbstractState =
        OctagonElinaAbstractState(abstract, vars)

    override fun top(): ElinaAbstractState = Companion.top()
    override fun bottom(): ElinaAbstractState = Companion.bottom()
}

class PolyhedralElinaAbstractState(abstract: Abstract0, vars: List<String>) :
    ElinaAbstractState(man, abstract, vars) {
    companion object {
        val man = OptPoly(false)

        fun bottom() = PolyhedralElinaAbstractState(Abstract0(man, 0, 0, true), listOf())
        fun top() = PolyhedralElinaAbstractState(Abstract0(man, 0, 0, false), listOf())
    }

    override fun construct(abstract: Abstract0, vars: List<String>): ElinaAbstractState =
        PolyhedralElinaAbstractState(abstract, vars)

    override fun top(): ElinaAbstractState = Companion.top()
    override fun bottom(): ElinaAbstractState = Companion.bottom()
}