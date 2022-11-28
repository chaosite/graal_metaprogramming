package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.Listable
import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import org.jgrapht.alg.connectivity.ConnectivityInspector
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod

internal class AnalysisGraphTest {
    private val methodToGraph = MethodToGraph()
    private val maximum = Listable::maximum.javaMethod
    private val maximumGraph = methodToGraph.getAnalysisGraph(maximum)

    @Test
    fun `analysis graph is weakly connected`() {
        val inspector = ConnectivityInspector(maximumGraph)

        assertTrue(inspector.isConnected)
    }
}