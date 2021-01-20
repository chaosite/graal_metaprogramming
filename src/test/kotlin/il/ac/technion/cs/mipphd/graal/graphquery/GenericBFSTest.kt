package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.GraalAdapter
import il.ac.technion.cs.mipphd.graal.Listable
import il.ac.technion.cs.mipphd.graal.MethodToGraph
import org.graalvm.compiler.nodes.ValuePhiNode
import org.graalvm.compiler.nodes.ValueProxyNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod
import kotlin.streams.toList

internal class GenericBFSTest {
    val methodToGraph = MethodToGraph()
    val maximum = Listable::maximum.javaMethod

    @Test
    fun `maximum query matches maximum function ir`() {
        val cfg = methodToGraph.getCFG(maximum)
        val query = GraphMaker.createMaxGraph()
        val vertex = query.vertexSet().first()

        val results = bfsMatch(query, GraalAdapter.fromGraal(cfg), vertex)

        assertEquals(4, results.size)
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

        assertEquals(6, results.size)
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

        val results = bfsMatch(query, GraalAdapter.fromGraal(cfg), vertex)

        assertEquals(1, results.size)
    }


    @Test
    fun `possibleChildrenMatches returns ?? in non-Kleene case`() {
    }

    @Test
    fun `possibleChildrenMatches returns ?? in Kleene case`() {
        val cfg = methodToGraph.getCFG(maximum)
        val query = GraphMaker.createMaxGraph()
        val queryReturnKleene = query.edgeSet().stream()
            .filter{ it.getMatchType() == GraphQueryEdgeMatchType.KLEENE }
            .map(query::getEdgeSource)
            .toList()
            .first { it.clazz == ValueProxyNode::class.java }
        val adaptedCfg = GraalAdapter.fromGraal(cfg)
        val graphReturnKleene = adaptedCfg.vertexSet().stream()
            .filter { it.node.asNode().javaClass == ValuePhiNode::class.java }
            .map(adaptedCfg::outgoingEdgesOf)
            .toList()
            .last()
            .map(adaptedCfg::getEdgeTarget)
            .last { it.node.javaClass == ValueProxyNode::class.java }
        assertTrue(queryReturnKleene.match(graphReturnKleene)) // sanity

        val result = possibleChildrenMatches(query, adaptedCfg, queryReturnKleene, graphReturnKleene)
        println(result)
    }

    @Test
    fun permutations() {
    }

    @Test
    fun cartesianProduct() {
    }
}