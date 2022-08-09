package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter
import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import org.graalvm.compiler.nodes.java.LoadFieldNode
import org.graalvm.compiler.nodes.java.StoreFieldNode
import org.junit.jupiter.api.Test
import java.io.StringWriter
import kotlin.reflect.jvm.javaMethod

external fun anyUser(any: Any?): Boolean
data class AnyHolder(var any: Any? = null, var other: Any? = null)

fun anyHolder(param: String?): AnyHolder {
    val first = AnyHolder(param ?: "")
    anyUser(first)
    val second = AnyHolder(first, param ?: "") // AnyHolder(if(anyUser(param)) first else param)
    anyUser(second)
    val third = AnyHolder(second)
    anyUser(third)
    return third
}

fun anyHolder2(param: String?): AnyHolder {
    val first = AnyHolder() // alloc 85
    anyUser(first) // order is important - otherwise it will be optimized away and there will be no stores
    first.any = param ?: "" // param0, const"", alloc line 151
    first.other = null // const null

    val second = AnyHolder() // alloc 89
    anyUser(second)
    second.any = first // alloc line 151
    second.other = param ?: "" // param0, const"", alloc line 156

    val third = AnyHolder() // alloc 93
    anyUser(third)
    third.any = second // alloc line 156
    third.other = null // const null

    val fourth = AnyHolder() // alloc 97
    anyUser(fourth)
    fourth.any = second.other
    fourth.other = third.any

    return third
}


class PointsToAnalysisTests {

        val methodToGraph = MethodToGraph()

        @Test
        fun `print anyHolder graphs`() {
            val cfg = methodToGraph.getCFG(::anyHolder.javaMethod)
            val adapted = GraalAdapter.fromGraal(cfg)

            val sw = StringWriter()
            adapted.exportQuery(sw)

            println(sw.buffer)
        }

        @Test
        fun `get pointsto graph preliminaries of anyHolder`() {
            val cfg = methodToGraph.getCFG(::anyHolder.javaMethod)
            val graph = GraalAdapter.fromGraal(cfg)
            val (values, allocations, associated) = PointsToAnalysis(graph).getPointsToGraphPreliminiaries()
            values.filter { it.first.node is StoreFieldNode }.forEach(::println)
            println()
            allocations.forEach(::println)
            println()
            associated.forEach(::println)
        }

        @Test
        fun `get pointsto graph of anyHolder`() {
            val cfg = methodToGraph.getCFG(::anyHolder.javaMethod)
            val graph = GraalAdapter.fromGraal(cfg)
            val (nodes, edges) = PointsToAnalysis(graph).getPointsToGraph()
            var i = 1
            val numberedNodes = nodes.associateWith { i++ }
            val edgesStrings = edges.map { (from, to) ->
                val fromId = numberedNodes[from]!!
                val toId = numberedNodes[to]!!
                "$fromId -> $toId"
            }
            val graphFormat = """
digraph G {
${numberedNodes.entries.joinToString("\n") { "    ${it.value} [label=\"${it.key.toString().replace("\"", "'")}\"];" }}
${edgesStrings.joinToString("\n") { "    $it [ color=\"${if(edgesStrings.count { itt-> itt.split(" -> ")[0] == it.split(" -> ")[0] } == 1) "blue" else "red"}\" ];" }}
}
            """
            println(graphFormat)

        }

        @Test
        fun `print anyHolder2 graphs`() {
            val cfg = methodToGraph.getCFG(::anyHolder2.javaMethod)
            val adapted = GraalAdapter.fromGraal(cfg)

            val sw = StringWriter()
            adapted.exportQuery(sw)

            val rawGraph = sw.buffer.toString().split("\n")
            val frameStateNodes = rawGraph.filter { it.contains("FrameState") }
                .map { it.trim().split(" ")[0] }.toSet()
            println(rawGraph.filter {
                "FrameState" !in it && frameStateNodes.all { itt -> " $itt -" !in it && "> $itt " !in it }
            }.joinToString("\n"))
        }

        @Test
        fun `get pointsto graph preliminaries of anyHolder2`() {
            val cfg = methodToGraph.getCFG(::anyHolder2.javaMethod)
            val graph = GraalAdapter.fromGraal(cfg)
            val (values, allocations, associated) = PointsToAnalysis(graph).getPointsToGraphPreliminiaries()
            values.filter { it.first.node is StoreFieldNode }.forEach(::println)
            println()
            allocations.forEach(::println)
            println()
            associated.forEach(::println)
        }

        @Test
        fun `get pointsto graph of anyHolder2`() {
            val cfg = methodToGraph.getCFG(::anyHolder2.javaMethod)
            val graph = GraalAdapter.fromGraal(cfg)
            val (nodes, edges) = PointsToAnalysis(graph).getPointsToGraph()
            var i = 1
            val numberedNodes = nodes.associateWith { i++ }
            val edgesStrings = edges.map { (from, to) ->
                val fromId = numberedNodes[from]!!
                val toId = numberedNodes[to]!!
                "$fromId -> $toId"
            }
            val graphFormat = """
digraph G {
${numberedNodes.entries.joinToString("\n") { "    ${it.value} [label=\"${it.key.toString().replace("\"", "'")}\"];" }}
${edgesStrings.joinToString("\n") { "    $it [ color=\"${if(edgesStrings.count { itt-> itt.split(" -> ")[0] == it.split(" -> ")[0] } == 1) "blue" else "red"}\" ];" }}
}
            """
            println(graphFormat)

        }
    }
