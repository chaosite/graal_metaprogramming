package il.ac.technion.cs.mipphd.graal.elina

import il.ac.technion.cs.mipphd.graal.domains.Monom
import il.ac.technion.cs.mipphd.graal.domains.PolyhedralAbstractState
import il.ac.technion.cs.mipphd.graal.domains.SymbolicLinExpr
import il.ac.technion.cs.mipphd.graal.domains.toMpq
import org.amshove.kluent.internal.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PolyhedralAbstractStateTest {
    @Test
    fun `test declare variable`() {
        val state1 = PolyhedralAbstractState()
        val state2 = state1.declareVariable("foo")
        assertTrue(state2.isDeclared("foo"))
        println(state2)
    }

    @Test
    fun `test assign`() {
        val state1 = PolyhedralAbstractState()
        val state2 = state1.declareVariable("foo")
        val state3 = state2.assign("foo", 4)
        println(state3)
    }

    @Test
    fun `multiple assign`() {
        val s1 = PolyhedralAbstractState()
        val s2 = s1.declareVariable("foo").declareVariable("bar").declareVariable("buzz")
        val s3 = s2.assign("bar", 2.0)
        val s4 = s3.assign("buzz", 3.5)
        val s5 = s4.assign("foo", SymbolicLinExpr(
            listOf(Monom("bar", 3.toMpq()),
                Monom("buzz", 13.toMpq()))))
        println(s5)
    }

    @Test
    fun `assign and assume`() {
        val s = PolyhedralAbstractState()
            .declareVariable("foo")
            .declareVariable("bar")
            .declareVariable("buzz")
            .assume("foo", "<=", 5)
            .assume("foo", "<=", 12.5)
            .assign("bar", SymbolicLinExpr(listOf(Monom("foo", 2.5.toMpq()), Monom("buzz", 7.toMpq()))))

        println(s)
    }

    @Test
    fun `this should not be bottom`() {
        val s = PolyhedralAbstractState()
            .assume("phi42", "!=", 0.0)
            .assume("phi41", ">=", 101)

        println(s)
        assertFalse(s.isBottom())
    }

    @Test
    fun `join two abstract states`() {
        val s1 = PolyhedralAbstractState()
            .declareVariable("foo")
            .assume("foo", ">=", 5)
        val s2 = PolyhedralAbstractState()
            .declareVariable("foo")
            .assume("foo", ">=", 12.5)
        val s3 = s1.join(s2)

        println("$s1 + $s2 = $s3")
    }

    @Test
    fun `try widen`() {
        val s1 = PolyhedralAbstractState()
            .declareVariable("foo")
            .assume("foo", ">=", 5)
        val s2 = PolyhedralAbstractState()
            .declareVariable("foo")
            .assume("foo", "<=", 12.5)
        val s3 = s1.widen(s2)

        println("$s1 + $s2 = $s3")
    }

    @Test
    fun `test equality`() {
        val s1 = PolyhedralAbstractState()
            .declareVariable("foo")
            .assume("foo", ">=", 5)
        val s2 = PolyhedralAbstractState()
            .declareVariable("foo")
            .assume("foo", ">=", 5.5)

        assertEquals(s1, s2, "Not equal")
    }

    @Test
    fun `assign assumed var, second var undeclared`() {
        for (i in 1..100) {
            val s = PolyhedralAbstractState()
                .declareVariable("parameter1")
                .assume("parameter1", "<", 101.0)
                .assign("phi38", SymbolicLinExpr(listOf(Monom("parameter1", 1.0.toMpq())), 11.0.toMpq()))

            println(s)
        }
    }

    @Test
    fun `conform test`() {
        val s1 = PolyhedralAbstractState()
            .declareVariable("a")
            .assume("a", ">", 3)
        val s2 = PolyhedralAbstractState()
            .declareVariable("b")
            .assume("b", "<", 5)

        val s21 = s2.conform(s1)
        val s12 = s1.conform(s2)

        println("$s21, $s12")
    }

    @Test
    fun `join test`() {
        val s1 = PolyhedralAbstractState()
            .assume("x", ">", 5)
        val s2 = PolyhedralAbstractState()
            .assume("x", ">", 0)
        val s3 = s1.join(s2)
        println(s1)
        println(s2)
        println(s3)
    }
}