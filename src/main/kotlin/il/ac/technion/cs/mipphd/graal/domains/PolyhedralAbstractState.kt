package il.ac.technion.cs.mipphd.graal.domains

import apron.*
import elina.OptPoly

data class Monom(val name: String, val coeff: Coeff)

data class SymbolicLinExpr(
    val monoms: List<Monom> = listOf(),
    val constant: Int = 0
) {
    fun toLinexpr(): Linexpr0 {
        val ret = Linexpr0(true, monoms.size)

        for (i in 0..monoms.size) {
            val term = ret.linterms[i]
            term.dimension = i
            term.setCoeffcient(monoms[i].coeff)
        }

        val cst = Scalar.create()
        cst.set(constant)
        ret.cst = cst

        return ret
    }
}

class PolyhedralAbstractState(
    protected val abstract: Abstract0 = Abstract0(man, 0, 0),
    val vars: List<String> = listOf()
) {
    companion object {
        val man = OptPoly(false)
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

    fun assign(name: String, rhs: Linexpr0): PolyhedralAbstractState {
        val (o, dim) = varToDim(name)
        return PolyhedralAbstractState(o.abstract.assignCopy(man, dim, rhs, null),
            o.vars)
    }

    private fun varToDim(name: String): Pair<PolyhedralAbstractState, Int> {
        val o = if (name in vars) this else declareVariable(name)
        return Pair(o, vars.indexOf(name))
    }
}