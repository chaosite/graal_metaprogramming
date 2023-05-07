package il.ac.technion.cs.mipphd.graal.domains

import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod

internal class DomainsTest {


    @Nested
    @DisplayName("Prerequisites")
    inner class PrerequisiteTests {
        private val mccarthy91Method = ::mccarthy91.javaMethod
        private val methodToGraph = MethodToGraph(true)
        private val mccarthy91Graph = methodToGraph.getAnalysisGraph(mccarthy91Method)

        @Test
        fun `display mccarthy 91 function`() {
            println(mccarthy91Graph.export())
        }

        @Test
        fun `analyze mccarthy 91 function via graph executor`() {
            val executor = McCarthy91Analysis(mccarthy91Graph,
                OctagonElinaAbstractState.top().assume("parameter1", "<=", 101)
            )

            val results = executor.iterateUntilFixedPoint()
            val items = results.toList().asSequence().sortedBy { it.first.index }.map { it.second }
            for (item in items) {
                if (item.statements.isNotEmpty()) {
                    println("# polystate_in: ${item.polyhedralAbstractState_in}")
                    println(item.statements)
                    println("# polystate: ${item.polyhedralAbstractState}")
                    println()
                }
            }

             //val finalState = results[results.keys.find { it.index.toInt() == 26 }]?.polyhedralAbstractState!!.assume("phi41", ">=", 0)

             //println("final bound for phi41: ${finalState.getBound("phi41")}")

            println(mccarthy91Graph.export())
        }

        @Test
        fun `analyze McCarthy 91 function via graph executor and print graph`() {
            val executor = McCarthy91Analysis(mccarthy91Graph,
                OctagonElinaAbstractState.top().assume("parameter1", "<=", 101))

            executor.iterateUntilFixedPoint()

            println(mccarthy91Graph.export())
        }

        @Test
        fun `print McCarthy 91 as souffle`() {
            println(mccarthy91Graph.exportSouffle())
        }

        @Test
        fun `analyze simple loop via graph executor`() {
            val graph = methodToGraph.getAnalysisGraph(::simple_loop.javaMethod)
            val executor = McCarthy91Analysis(graph)

            val results = executor.iterateUntilFixedPoint()
            val items = results.toList().asSequence().sortedBy { it.first.index }.map { it.second }
            for (item in items) {
                if (item.statements.isNotEmpty()) {
                    println("# polystate_in: ${item.polyhedralAbstractState_in}")
                    println(item.statements)
                    println("# polystate: ${item.polyhedralAbstractState}")
                    println()
                }
            }
        }
    }
}