package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter
import il.ac.technion.cs.mipphd.graal.Listable
import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import org.graalvm.compiler.nodes.PhiNode
import org.graalvm.compiler.nodes.ValuePhiNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod

internal class GenericBFSTest {
    private val methodToGraph = MethodToGraph()
    private val maximum = Listable::maximum.javaMethod

    @Test
    fun `maximum query matches maximum function ir`() {
        val cfg = methodToGraph.getCFG(maximum)
        val query = GraphMaker.createMaxGraph()
        val vertex = query.vertexSet().first()

        val results = bfsMatch(query, GraalAdapter.fromGraal(cfg), vertex)

        assertEquals(18, results.size)
    }

    @Test
    fun `minimal query matches maximum function ir`() {
        val cfg = methodToGraph.getCFG(maximum)
        val query = GraphMaker.createMinimalQuery()
        val vertex = query.vertexSet().first()

        val results = bfsMatch(query, GraalAdapter.fromGraal(cfg), vertex)

        assertEquals(2, results.size)
    }

    @Test
    fun `minimal kleene query matches maximum function ir`() {
        val cfg = methodToGraph.getCFG(maximum)
        val query = GraphMaker.createMinimalKleeneQuery()
        val vertex = query.vertexSet().first()

        val results = bfsMatch(query, GraalAdapter.fromGraal(cfg), vertex)

        assertEquals(22, results.size)
    }

    @Test
    fun `two vertex one edge query matches maximum function ir`() {
        val cfg = methodToGraph.getCFG(maximum)
        val query = GraphMaker.createTwoVertexOneEdgeQuery()
        val vertex = query.vertexSet().last()

        val results = bfsMatch(query, GraalAdapter.fromGraal(cfg), vertex)

        assertEquals(2, results.size)
    }

    @Test
    fun `kleene query with return matches maximum function ir`() {
        val cfg = methodToGraph.getCFG(maximum)
        val query = GraphMaker.createSimpleKleeneQueryWithReturn()
        val vertex = query.vertexSet().first()
        val graph = GraalAdapter.fromGraal(cfg)

        val results = bfsMatch(query, graph, vertex)

        println(results)

        assertEquals(1, results.size)
    }


    @Test
    fun `possibleChildrenMatches returns something in non-Kleene case`() {
    }

    @Test
    fun `possibleChildrenMatches returns something in Kleene case`() {
        val cfg = methodToGraph.getCFG(maximum)
        val query = GraphMaker.createMaxGraph()
        val queryReturnPhi = query.edgeSet().stream()
            .filter{ it.matchType == GraphQueryEdgeMatchType.KLEENE }
            .map(query::getEdgeSource)
            .toList()
            .first { it.clazz == PhiNode::class.java }
        val queryReturnKleene = query.getEdgeTarget(query.outgoingEdgesOf(queryReturnPhi).last())
        val adaptedCfg = GraalAdapter.fromGraal(cfg)
        val graphReturnPhi = adaptedCfg.vertexSet().stream()
            .filter { it.node.asNode().javaClass == ValuePhiNode::class.java }
            .toList()
            .last()
        val result = possibleChildrenMatches(query, adaptedCfg, queryReturnPhi, graphReturnPhi)

        assertTrue(queryReturnPhi.match(graphReturnPhi)) // sanity
        println(result)
        assertEquals(2, result.maxOf { it[queryReturnKleene]!!.size })
    }

    @Test
    fun permutations() {
    }

    @Test
    fun cartesianProduct() {
    }
}