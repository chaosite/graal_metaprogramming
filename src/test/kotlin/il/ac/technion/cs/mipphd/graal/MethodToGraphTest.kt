package il.ac.technion.cs.mipphd.graal

import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import il.ac.technion.cs.mipphd.graal.utils.SourcePosTool
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod

class AddTwoNumbers {
    fun add(x: Int, y: Int) = x+y
}

fun simpleFib(n: Int): Int =
    try {
    if(n == 0 || n == 1) { 1 } else {
        simpleFib(n - 1) + simpleFib(n - 2)
    }
} catch(e: Throwable) { 0 }

class Fib {
    var counter = 0L

    fun incrementCounter() {
        counter += 1
    }

    fun fib(n: Long): Long {
        val map = mutableMapOf<Long, Long>()
        return when(n) {
            0L -> 1 + counter
            1L -> 1 + counter
            else -> map.computeIfAbsent(n - 1, ::fib) + map.computeIfAbsent(n - 2, ::fib)
        }
    }
}

internal class MethodToGraphTest {
    val methodToGraph = MethodToGraph(false)
    val addNumbersMethod = AddTwoNumbers::class.java.methods[0]
    val fibMethod = Fib::class.java.methods.find { it.name == "fib" }

    @Test
    fun `print graph of fib function`() {
        println(methodToGraph.getAnalysisGraph(fibMethod).export())
    }

    @Test
    fun `print graph of simpleFib function`() {
        println(methodToGraph.getAnalysisGraph(::simpleFib.javaMethod).export())
    }

    @Test
    fun `print sanitized graph of simpleFib function`() {
        val graph = methodToGraph.getAnalysisGraph(::simpleFib.javaMethod)
        graph.removeExceptions()
        println(graph.export())
    }

    @Test
    fun `get cfg does not throw exception`() {
        methodToGraph.getCFG(addNumbersMethod)
    }

    @Test
    fun `test calling print cfg method`() {
        println("Method is: ${addNumbersMethod.name}")
        methodToGraph.printCFG(methodToGraph.getCFG(addNumbersMethod).asCFG())
    }

    @Test
    fun `test source position`() {
        val cfg = methodToGraph.getCFG(addNumbersMethod)
        val returnNode = cfg.asCFG().startBlock.endNode

        // val nodeSourcePosition = returnNode.nodeSourcePosition
        println(returnNode.nodeSourcePosition)
        println(SourcePosTool.getBCI(returnNode))
        println(SourcePosTool.getLocation(returnNode))

        println(SourcePosTool.getStackTraceElement(returnNode).className)
        println(SourcePosTool.getStackTraceElement(returnNode).methodName)
        println(SourcePosTool.getStackTraceElement(returnNode).fileName)
        println(SourcePosTool.getStackTraceElement(returnNode).lineNumber)
    }
}