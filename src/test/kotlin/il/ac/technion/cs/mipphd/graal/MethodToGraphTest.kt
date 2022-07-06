package il.ac.technion.cs.mipphd.graal

import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import org.junit.jupiter.api.Test

class AddTwoNumbers {
    fun add(x: Int, y: Int) = x+y
}

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
    val methodToGraph = MethodToGraph()
    val addNumbersMethod = AddTwoNumbers::class.java.methods[0]
    val fibMethod = Fib::class.java.methods.find { it.name == "fib" }


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