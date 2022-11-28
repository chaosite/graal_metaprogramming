package il.ac.technion.cs.mipphd.graal.elina

import il.ac.technion.cs.mipphd.graal.domains.*
import org.amshove.kluent.internal.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PolyhedralAbstractStateTest {
    @Test
    fun `test declare variable`() {
        val state1 = PolyhedralElinaAbstractState.top()
        val state2 = state1.declareVariable("foo")
        assertTrue(state2.isDeclared("foo"))
        println(state2)
    }

    @Test
    fun `test assign`() {
        val state1 = PolyhedralElinaAbstractState.top()
        val state2 = state1.declareVariable("foo")
        val state3 = state2.assign("foo", 4.toMpq())
        println(state3)
    }

    @Test
    fun `multiple assign`() {
        val s1 = PolyhedralElinaAbstractState.top()
        val s2 = s1.declareVariable("foo").declareVariable("bar").declareVariable("buzz")
        val s3 = s2.assign("bar", (2.0).toMpq())
        val s4 = s3.assign("buzz", (3.5).toMpq())
        val s5 = s4.assign("foo", SymbolicLinExpr(
            listOf(Monom("bar", 3.toMpq()),
                Monom("buzz", 13.toMpq()))))
        println(s5)
    }

    @Test
    fun `assign and assume`() {
        val s = PolyhedralElinaAbstractState.top()
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
        val s = PolyhedralElinaAbstractState.top()
            .assume("phi42", "!=", 0.0)
            .assume("phi41", ">=", 101)

        println(s)
        assertFalse(s.isBottom())
    }

    @Test
    fun `join two abstract states`() {
        val base = PolyhedralElinaAbstractState.top().declareVariable("foo")
        val s1 = base.assume("foo", ">=", 5)
        val s2 = base.assume("foo", ">=", 12.5)
        val result = s1.join(s2)

        assertEquals(s1, result)
    }

    @Test
    fun `meet two abstract states`() {
        val base = PolyhedralElinaAbstractState.top()
            .declareVariable("a")

        val s1 = base.assume("a", ">=", 5)
        val s2 = base.assume("a", "<=", 12.5)
        val result = s1.meet(s2)
        val expected = base.assume("a", ">=", 5).assume("a", "<=", 12.5)

        assertEquals(expected, result)
    }

    @Test
    fun `try widen`() {
        val base = PolyhedralElinaAbstractState.top().declareVariable("foo")
        val s1 = base.assume("foo", ">=", 12.5)
        val s2 = base.assume("foo", ">=", 5)
        val joined = s1.join(s2)

        println("after join: $joined")

        val widened = s1.widen(joined)

        println("after widened: $widened")
    }

    @Test
    fun `test equality`() {
        val s1 = PolyhedralElinaAbstractState.top()
            .declareVariable("foo")
            .assume("foo", ">=", 5.001)
        val s2 = PolyhedralElinaAbstractState.top()
            .declareVariable("foo")
            .assume("foo", ">=", 5.4)

        assertEquals(s1, s2, "Not equal")
    }

    @Test
    fun `assign assumed var, second var undeclared`() {
        for (i in 1..100) {
            val s = PolyhedralElinaAbstractState.top()
                .declareVariable("parameter1")
                .assume("parameter1", "<", 101.0)
                .assign("phi38", SymbolicLinExpr(listOf(Monom("parameter1", 1.0.toMpq())), 11.0.toMpq()))

            println(s)
        }
    }

    @Test
    fun `conform test`() {
        val s1 = PolyhedralElinaAbstractState.top()
            .declareVariable("a")
            .assume("a", ">", 3)
        val s2 = PolyhedralElinaAbstractState.top()
            .declareVariable("b")
            .assume("b", "<", 5)

        val s21 = s2.conform(s1)
        val s12 = s1.conform(s2)

        println("$s21, $s12")
    }

    @Test
    fun `join test`() {
        val s1 = PolyhedralElinaAbstractState.top()
            .assume("x", ">", 5)
        val s2 = PolyhedralElinaAbstractState.top()
            .assume("x", ">", 0)
        val s3 = s1.join(s2)
        println(s1)
        println(s2)
        println(s3)
    }

    @Test
    fun `forget test`() {
        val s1 = PolyhedralElinaAbstractState.top()
            .assume("x", ">=", 20)
            .assign("y", SymbolicLinExpr(20, Monom("x", 2)))
        val s2 = s1.forget("x")

        println(s1)
        println(s2)
        s2.testSanity()
    }

    @Test
    fun `manual mccarthy91`() {
        val start = PolyhedralElinaAbstractState.top()
            .assume("n", "<=", 101)
            .assume("n" , ">=", 0)
            .assign("c", 1)

        fun loop_iteration(start: ElinaAbstractState): ElinaAbstractState {
            val a = start
                .assume("c", ">", 0)
                .assign("c", SymbolicLinExpr(-1, Monom("c")))
            val aa = a
                .assume("n", ">", 100)
                .assign("n", SymbolicLinExpr(-10, Monom("n")))
            val ab = a
                .assume("n", "<=", 100)
                .assign("n", SymbolicLinExpr(11, Monom("n")))
                .assign("c", SymbolicLinExpr(2, Monom("c")))
            val b = aa.join(ab)

            println("loop iteration: $b ($aa, $ab)")
            return b
        }

        var i = 1

        println("start: $start")
        var state = start.join(loop_iteration(start))
        println("$i: $state")
        i += 1

        repeat(3) {
            // non-widening
            state = state.join(loop_iteration(state))
            println("$i: $state")
            i += 1
        }
        state = state.assume("c", "==", 0)

        println("start widening")

        repeat(3) {
            // widening
            state = state.widen(state.join(loop_iteration(state)))
            println("$i: $state")
            i += 1
        }

        println(state.getBound("n"))
    }
}