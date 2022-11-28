package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.utils.GraalIRGraph
import il.ac.technion.cs.mipphd.graal.Listable
import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import org.graalvm.compiler.nodes.PhiNode
import org.graalvm.compiler.nodes.ValuePhiNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.StringWriter
import kotlin.reflect.jvm.javaMethod

internal class GenericBFSTest {
    private val methodToGraph = MethodToGraph()
    private val maximum = Listable::maximum.javaMethod

    @Test
    fun `maximum query on maximum function ir`() {
        val graph = methodToGraph.getAnalysisGraph(maximum)

        val vertex = graph.vertexSet().find { it is AnalysisNode.IR && it.isType("StartNode") }

        println(graph.export())
    }


    @Test
    fun `bfsMatch doesn't return extra node in match`() {
        val graph = methodToGraph.getAnalysisGraph(::anyHolder2.javaMethod)

        val nopNodes = listOf(
            "Pi",
            "VirtualInstance",
            "ValuePhi",
            "VirtualObjectState",
            "MaterializedObjectState"
        ).map { if (it.endsWith("State")) it else "${it}Node" }
        val notValueNodes = listOf(
            "Pi",
            "VirtualInstance",
            "ValuePhi",
            "Begin",
            "Merge",
            "End",
            "FrameState",
            "VirtualObjectState",
            "MaterializedObjectState"
        ).map { if (it.endsWith("State")) it else "${it}Node" }
        val query = GraphQuery.importQuery("""
digraph G {
    storeNode [ label="(?P<store>)|is('StoreFieldNode')" ];
    nop [ label="(?P<nop>)|${nopNodes.joinToString(" or ") { "is('$it')" }}" ];
	value [ label="(?P<value>)|${notValueNodes.joinToString(" and ") { "not is('$it')" }}" ];

	value -> nop [ label="*|is('DATA')" ];
    nop -> storeNode [ label="name() = 'value'" ];
}
""")
        val results = bfsMatch(query, graph, query.vertexSet().last())

        val store = query.vertexSet().find { it.name == "storeNode" }!!
        val nop = query.vertexSet().find { it.name == "nop" }!!
        val value = query.vertexSet().find { it.name == "value" }!!

        for (result in results) {
            println("===")
            println("${result[store]}")
            println("${result[nop]}")
            println("${result[value]}")
            println()
            assertNotEquals(result[value], result[store])
        }
    }

    private fun hasLoop(i: List<Int>): Int {
        var n = 5
        for (e in i) {
            n += e*3
        }
        return n
    }

    @Test
    fun `show hasLoop`() {
        val graph = GraalIRGraph.fromGraal(methodToGraph.getCFG(::hasLoop.javaMethod))
        val sw = StringWriter()
        graph.exportQuery(sw)
        println(sw.buffer)
    }

    @Test
    fun `finds multiple sources to LoopBegin`() {
        val graph = methodToGraph.getAnalysisGraph(::hasLoop.javaMethod)
        val query = GraphQuery.importQuery("""
            digraph G {
                sources [ label="[](?P<sources>)|" ];
                destination [ label="(?P<destination>)|is('LoopBeginNode')" ];
                
                
                sources -> destination [ label = "is('CONTROL')" ];
            }
        """.trimIndent())

        val results = bfsMatch(query, graph, query.vertexSet().last())
        assertEquals(1, results.size)
        assertEquals(2, results[0].size)

        assertThrows<RuntimeException> {
            bfsMatch(query, graph, query.vertexSet().first())
        }
        println(results)
    }
}