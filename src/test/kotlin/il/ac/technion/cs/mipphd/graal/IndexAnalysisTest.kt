package il.ac.technion.cs.mipphd.graal

import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod

class Listable(val l: List<Int>) {
    fun firstEven(): Int {
        var i = 0
        while (i < l.size) {
            val elem = l[i]
            if (elem % 2 == 0)
                return elem
            ++i
        }
        throw RuntimeException()
    }

    fun maximum(): Int {
        var i = 0
        var max = -1
        while (i < l.size) {
            val elem = l[i]
            if (elem >= max)
                max = elem
            ++i
        }
        return max
    }
}

internal class IndexAnalysisTest {
    val methodToGraph = MethodToGraph()
    val firstEvenMethod = Listable::firstEven.javaMethod
    val maximum = Listable::maximum.javaMethod
    @Test
    fun `run analysis on firstEven`() {
        // TODO: Rewrite
    }

    @Test
    fun `run analysis on maximum`() {
        // TODO: Rewrite
    }
}