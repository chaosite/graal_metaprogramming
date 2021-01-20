package il.ac.technion.cs.mipphd.graal

import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod

class Listable(val l: List<Int>) {
    fun firstEven(): Int {
        var i = 0;
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
        val cfg = methodToGraph.getCFG(firstEvenMethod).asCFG()
        val exitBlocks = cfg.blocks.filter { it.successorCount == 0 }
        val analysis = IndexAnalysis(cfg.blocks.toList(), listOf(cfg.startBlock), exitBlocks)
        analysis.doAnalysis()
        val results = analysis.results
        for (r in results) {
            println(r)
            println(r.callTarget().arguments())
        }
    }

    @Test
    fun `run analysis on maximum`() {
        val cfg = methodToGraph.getCFG(maximum).asCFG()
        val exitBlocks = cfg.blocks.filter { it.successorCount == 0 }
        val analysis = IndexAnalysis(cfg.blocks.toList(), listOf(cfg.startBlock), exitBlocks)
        analysis.doAnalysis()
        for (r in analysis.results) {
            println(r)
            println(r.callTarget().arguments())
        }
    }
}