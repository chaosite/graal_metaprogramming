package il.ac.technion.cs.mipphd.graal.elina

import apron.Abstract0
import elina.OptPoly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class BasicTests {
    @Test
    fun `load polyhedral domain`() {
        val man = OptPoly(false)
        val top = Abstract0(man, 2, 1)
        val bottom = Abstract0(man, 2, 1, true)
        val a0 = Abstract0(man, 2, 1)

        println("top: $top")
        println("bottom: $bottom")
        println("a0: $a0")

        assertTrue(top.isTop(man) && !top.isBottom(man))
        assertTrue(bottom.isBottom(man) && !bottom.isTop(man))
    }
}