package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.Listable
import il.ac.technion.cs.mipphd.graal.MethodToGraph
import org.graalvm.compiler.graph.NodeInterface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.io.StringWriter
import kotlin.reflect.jvm.javaMethod

val maximumQueryText = """
    digraph G {
      n2021302077 [ label="is('Invoke') and method().name = 'get' and method().className = 'java.util.List'" ];
      n1948571966 [ label="is('ValueNode')" ];
      n1689892436 [ label="is('calc.ConditionalNode')" ];
      n818409230 [ label="is('LogicNode')" ];
      n484969820 [ label="is('PhiNode')" ];
      n1314376406 [ label="is('ValueProxyNode')" ];
      n1744166494 [ label="is('ReturnNode')" ];
      n2021302077 -> n1948571966 [ label="DATA" ];
      n1948571966 -> n1689892436 [ label="DATA*" ];
      n818409230 -> n1689892436 [ label="BOTH" ];
      n1689892436 -> n484969820 [ label="DATA" ];
      n484969820 -> n1689892436 [ label="DATA" ];
      n484969820 -> n1314376406 [ label="DATA" ];
      n1314376406 -> n1744166494 [ label="DATA*" ];
    }
""".trimIndent()

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

    @Test
    fun `export-import round trip does not throw`() {
        val writer = StringWriter()
        GraphMaker.createMaxGraph().exportQuery(writer)
        val exported = writer.buffer.toString()
        val reader = StringReader(exported)
        GraphQuery.importQuery(reader)
    }

    @Test
    fun `imported maximum query matches maximum function ir`() {
        val cfg = methodToGraph.getCFG(maximum)
        val query = GraphQuery.importQuery(StringReader(maximumQueryText))


        val writer = StringWriter()
        query.exportQuery(writer)
        println(writer.buffer.toString())

        val set = mutableSetOf(*cfg.asCFG().graph.nodes.toList().toTypedArray())
        query.vertexSet().forEach { queryNode ->
            assertTrue(set.any { (queryNode as GraphQueryVertex<NodeInterface>).match(it) }, "Couldn't find $queryNode")
        }
        assertEquals(4, query.match(cfg).size)
    }
}