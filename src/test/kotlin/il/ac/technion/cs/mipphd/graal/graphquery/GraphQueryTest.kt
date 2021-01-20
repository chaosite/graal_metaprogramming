package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.Listable
import il.ac.technion.cs.mipphd.graal.MethodToGraph
import org.graalvm.compiler.graph.NodeInterface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.StringWriter
import kotlin.reflect.jvm.javaMethod

internal class GraphQueryTest {
    val methodToGraph = MethodToGraph()
    val maximum = Listable::maximum.javaMethod
    val maximumQuery = GraphMaker.createMaxGraph()

    @Test
    fun `all nodes in query match some node in actual graph`() {
        val cfg = methodToGraph.getCFG(maximum)
        val set = mutableSetOf(*cfg.asCFG().graph.nodes.toList().toTypedArray())
        maximumQuery.vertexSet().forEach { queryNode ->
            assertTrue(set.any { (queryNode as GraphQueryVertex<NodeInterface>).match(it) }, "Couldn't find $queryNode")
        }
    }

    @Test
    fun `maximum query matches maximum function ir`() {
        val cfg = methodToGraph.getCFG(maximum)
        assertEquals(4, maximumQuery.match(cfg).size)
    }

    @Test
    fun `minimal query matches maximum function ir`() {
        val cfg = methodToGraph.getCFG(maximum)
        assertEquals(2, GraphMaker.createMinimalQuery().match(cfg).size)
    }

    @Test
    fun `two vertex one edge query matches maximum function ir`() {
        val cfg = methodToGraph.getCFG(maximum)
        assertEquals(2, GraphMaker.createTwoVertexOneEdgeQuery().match(cfg).size)
    }

    @Test
    fun `export does not throw`() {
        val writer = StringWriter()
        GraphMaker.createMaxGraph().exportQuery(writer)
        println(writer.buffer.toString())
    }
}