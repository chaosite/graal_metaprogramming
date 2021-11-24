package il.ac.technion.cs.mipphd.graal.graphquery

import arrow.core.extensions.map.foldable.find
import il.ac.technion.cs.mipphd.graal.Listable
import il.ac.technion.cs.mipphd.graal.MethodToGraph
import il.ac.technion.cs.mipphd.graal.graphquery.GraphMaker.*
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import org.graalvm.compiler.graph.NodeInterface
import org.graalvm.compiler.nodes.ValuePhiNode
import org.graalvm.compiler.nodes.calc.AddNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.PrintWriter
import java.io.StringReader
import java.io.StringWriter
import java.util.*
import java.util.function.BiConsumer
import java.util.function.Consumer
import kotlin.collections.HashMap
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
      n2021302077 -> n1948571966 [ label="*|is('DATA')" ];
      n1948571966 -> n1689892436 [ label="is('DATA')" ];
      n818409230 -> n1689892436 [ label="is('DATA') or is('CONTROL')" ];
      n1689892436 -> n484969820 [ label="is('DATA')" ];
      n484969820 -> n1689892436 [ label="is('DATA')" ];
      n484969820 -> n1314376406 [ label="*|is('DATA')" ];
      n1314376406 -> n1744166494 [ label="is('DATA')" ];
    }
""".trimIndent()

val repeatedNodesQueryText = """
    digraph G {
    	framestate [ label="is('FrameState')"];
    	merge [ label="1 = 1"]
    	values [ label="[]|1 = 1"]

    	values -> framestate [ label = "is('DATA') and name() = 'values'"];
    	framestate -> merge [ label = "is('DATA') and name() = 'stateAfter'"];
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
            assertTrue(set.any { (queryNode as GraphQueryVertex<*>).match(it) }, "Couldn't find $queryNode")
        }
    }

    @Test
    fun `maximum query matches maximum function ir`() {
        val cfg = methodToGraph.getCFG(maximum)
        assertEquals(18, maximumQuery.match(cfg).size)
    }

    @Test
    fun `minimal query matches maximum function ir`() {
        val cfg = methodToGraph.getCFG(maximum)
        assertEquals(2, GraphMaker.createMinimalQuery().match(cfg).size)
    }

    @Test
    fun `repeated query matches maximum function ir`() {
        val cfg = methodToGraph.getCFG(maximum)
        val results = GraphQuery.importQuery(repeatedNodesQueryText).match(cfg)

        println(results)

        assertEquals(19, results.size)
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
        assertEquals(18, query.match(cfg).size)
    }

    @Test
    fun `imported anon graph query union`() {
        val cfg = AnonGraphReduced();
        val query = UnionUsingArrayDotQuery();
        val strwriter = StringWriter()
        query.exportQuery(strwriter)
        println(strwriter)
        val results = query.match(cfg);
        val path = "/home/dor/Desktop/PG/dots/unionquery/"
        results.forEachIndexed { i, r ->
            val writer = PrintWriter("$path$i.dot", "UTF-8")
            cfg.exportQuery(writer, query, r.toMap());
        }
    }


    @Test
    fun `imported anon graph query loop with index and loadfield`() {
        val cfg = AnonGraphReduced();
        val query = LoopWithIteratorWithExtraStepsDotQuery();
        val results = query.match(cfg);
        val path = "/home/dor/Desktop/PG/dots/loops/"
        val strwriter = StringWriter()
        query.exportQuery(strwriter)
        println(strwriter)
        results.forEachIndexed { i, r ->
            val writer = PrintWriter("$path$i.dot", "UTF-8")
            cfg.exportQuery(writer, query, r.toMap());
        }
    }

    @Test
    fun `imported anon graph query phi inc`() {
        val cfg = AnonGraphReduced();
        val query = PhiNodesIncDotQuery();
        val results = query.match(cfg);
        val path = "/home/dor/Desktop/PG/dots/incphi/"
        val strwriter = StringWriter()
        query.exportQuery(strwriter)
        println(strwriter)
        results.forEachIndexed { i, r ->
            val writer = PrintWriter("$path$i.dot", "UTF-8")
            cfg.exportQuery(writer, query, r.toMap());
        }
    }

    @Test
    fun `imported anon graph query split node`() {
        val cfg = AnonGraphReduced();
        val query = SplitCollectionIndexedPairDotQuery();
        val results = query.match(cfg);
        val strwriter = StringWriter()
        query.exportQuery(strwriter)
        println(strwriter)
        val path = "/home/dor/Desktop/PG/dots/splitnode/"
        results.forEachIndexed { i, r ->
            val writer = PrintWriter("$path$i.dot", "UTF-8")

            cfg.exportQuery(writer, query, r.toMap());

        }
    }

    @Test
    fun `imported anon graph query combined`() {
        val path = "/home/dor/Desktop/PG/dots/matchquries1/"
        val cfg = AnonGraphReduced();
        val unionQuery = UnionUsingArrayDotQuery()
        val splitNodeQuery = SplitCollectionIndexedPairDotQuery()
//        val loopIndexQuery = LoopWithIteratorWithExtraStepsDotQuery()
        val loopIndexQuery = PhiNodesIncDotQuery()
        val phiNodeLoop = loopIndexQuery.vertexSet().find { v -> v.label().contains("Phi") }
        val phiNodeSplit = splitNodeQuery.vertexSet().find { v -> v.label().contains("Phi") }
        val unionResults = unionQuery.match(cfg)
        val splitResults = splitNodeQuery.match(cfg)
        val loopsResults = loopIndexQuery.match(cfg)
        var i = 0
        unionResults.forEach() { unionR ->
            splitResults.forEach() { splitR ->
                loopsResults.forEach() { loopR ->
                    val valueNode = unionR.keys.find { v -> v.captureGroup() == Optional.of("value") }
                    val unionM = unionR[valueNode]
                    val collectionNode = splitR.keys.find { v -> v.captureGroup() == Optional.of("collection") }
                    val splitM = splitR[collectionNode]
                    val splitPhi = splitR[phiNodeSplit]
                    val loopM = loopR[phiNodeLoop]
                    if (loopM != null && splitM != null && unionM != null && splitPhi != null) {
                        if (splitM[0] == unionM[0] && splitPhi[0] == loopM[0]) {
                            val combinedMap = unionR.toMutableMap()
                            combinedMap.clear()
                            combinedMap.putAll(unionR)
                            combinedMap.putAll(splitR)
                            combinedMap.putAll(loopR)
                            val writer = PrintWriter("$path$i.dot", "UTF-8")
                            i += 1
                            cfg.exportQuery(writer, null, combinedMap);
                        }
                    }
                }
            }
        }

    }

    @Test
    fun `imported anon graph query combined full`() {
        val cfg = AnonGraphReduced();
        val query = CombinedQuery();
        val results = query.match(cfg);
        val strwriter = StringWriter()
        query.exportQuery(strwriter)
        println(strwriter)
        val path = "/home/dor/Desktop/PG/dots/combined/"
        results.forEachIndexed { i, r ->
            val writer = PrintWriter("$path$i.dot", "UTF-8")

            cfg.exportQuery(writer, query, r.toMap());

        }
    }

    @Test
    fun `query test`() {
        val s = """
           digraph G {
  n674421699 [ label="is('LoopBeginNode')" ];
  n1322240894 [ label="is('ValuePhiNode')" ];
  n202106861 [ label="(?P<foo>)|is('calc.AddNode')" ];
  n1969077088 [ label="is('ValueNode')" ];
  n674421699 -> n1322240894 [ label="(1) = (1)" ];
  n202106861 -> n1322240894 [ label="(1) = (1)" ];
  n1322240894 -> n202106861 [ label="(1) = (1)" ];
  n1969077088 -> n1322240894 [ label="(1) = (1)" ];
}
        """.trimIndent()
        val cfg = AnonGraphReduced();
        val query = GraphQuery.importQuery(s)
        val results = query.match(cfg);
        val f = results[0].keys.find { v -> v.captureGroup() == Optional.of("foo") }
        println(f)
    }
}