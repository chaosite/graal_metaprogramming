package il.ac.technion.cs.mipphd.graal

import il.ac.technion.cs.mipphd.graal.graphquery.*
import il.ac.technion.cs.mipphd.graal.utils.GraalIRGraph
import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import il.ac.technion.cs.mipphd.graal.utils.SourcePosTool
import org.graalvm.compiler.nodes.ValueNode
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.lang.NullPointerException
import kotlin.reflect.jvm.javaMethod


internal class SourcePosToolTest {
    val methodToGraph = MethodToGraph()

    @Test
    fun `print sourcepos info for commitedobject`() {
        val graph = AnalysisGraph.fromIR(GraalIRGraph.fromGraal(methodToGraph.getCFG(::binTreeCycle.javaMethod)))
        val query = GraphQuery.importQuery(StringReader("""
            digraph {
                n [ label="is('CommitAllocationNode')" ]
            }
        """.trimIndent()))

        val results = bfsMatch(query, graph, query.vertexSet().first())
        val nodes = results.flatMap(Map<GraphQueryVertex, List<AnalysisNode>>::values).flatten()

        for (nodeWrapper in graph.vertexSet()) {
            nodeWrapper as AnalysisNode.IR
            val node = nodeWrapper.node()
            if (node is ValueNode) {
                try {
                    println(node)
                    println(node.nodeSourcePosition)
                    println(SourcePosTool.getBCI(node))
                    println(SourcePosTool.getLocation(node))

                    println(SourcePosTool.getStackTraceElement(node).className)
                    println(SourcePosTool.getStackTraceElement(node).methodName)
                    println(SourcePosTool.getStackTraceElement(node).fileName)
                    println(SourcePosTool.getStackTraceElement(node).lineNumber)
                } catch (e: NullPointerException) {
                    println("$node : NullPointerException")
                }
            }
            println()
            println()
        }
    }
}