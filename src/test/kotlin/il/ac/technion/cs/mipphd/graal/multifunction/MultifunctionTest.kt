package il.ac.technion.cs.mipphd.graal.multifunction

import il.ac.technion.cs.mipphd.graal.MethodToGraph
import il.ac.technion.cs.mipphd.graal.graphquery.GraphMaker
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import kotlin.reflect.jvm.javaMethod

fun a(x: String) = 5 + b(x)
fun b(x: String) = x.toLong() * 2
fun c(x: String) = a(x) + b(x)

internal class MultifunctionTest {
    private val methodToGraph = MethodToGraph()

    @Test
    @Disabled
    fun `recursively apply reactivize query`() {
        val results = recursivelyApplyQuery(GraphMaker.createValueToReturnQuery(), methodToGraph.lookupJavaMethodToWrapper(::c.javaMethod)) { true }
        println(results)
        listOf(::a.javaMethod, ::b.javaMethod, ::c.javaMethod).map(methodToGraph::lookupJavaMethodToWrapper).forEach { method ->
            assertTrue(results.containsKey(method), "$method not found")
            assertEquals(2, results[method]!!.size)
        }
    }
}