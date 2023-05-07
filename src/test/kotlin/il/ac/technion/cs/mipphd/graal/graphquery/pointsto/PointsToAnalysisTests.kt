package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.graphquery.pointsto.PointsToAnalysis
import il.ac.technion.cs.mipphd.graal.utils.GraalIRGraph
import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import org.junit.jupiter.api.Test
import java.io.StringWriter
import kotlin.properties.Delegates
import kotlin.reflect.jvm.javaMethod

external fun anyUser(any: Any?): Boolean
data class AnyHolder(var any: Any? = null, var other: Any? = null)

fun anyHolder(param: String?): AnyHolder { // TODO constructors don't work as expected
    val first = AnyHolder(param ?: "")
    anyUser(first)
    val second = AnyHolder(first, param ?: "") // AnyHolder(if(anyUser(param)) first else param)
    anyUser(second)
    val third = AnyHolder(second)
    anyUser(third)
    return third
}

fun anyHolderVariant(param: String?): AnyHolder {
    val first = AnyHolder()
    anyUser(first)
    first.any = param ?: ""
    val second = AnyHolder()
    anyUser(second)
    second.any = first
    second.other = param ?: ""
    val third = AnyHolder()
    anyUser(third)
    third.any = second
    return third
}

fun anyHolder2(param: String?): AnyHolder {
    val first = AnyHolder() // alloc 85
    anyUser(first) // order is important - otherwise it will be optimized away and there will be no stores
    first.any = param ?: "" // param0, const"", alloc line 151
    first.other = null // const null

    val second = AnyHolder() // alloc 89
    anyUser(second)
    second.any = first // alloc line 151
    second.other = param ?: "" // param0, const"", alloc line 156

    val third = AnyHolder() // alloc 93
    anyUser(third)
    third.any = second // alloc line 156
    third.other = null // const null

    val fourth = AnyHolder() // alloc 97
    anyUser(fourth)
    fourth.any = second.other
    fourth.other = third.any

    return fourth
}

fun anyHolder3(param: String?): AnyHolder {
    val first = AnyHolder() // 85
//    anyUser(first)
    first.any = param ?: ""
    first.other = null

    val second = AnyHolder() // 89
//    anyUser(second)
//    second.any = null
//    second.other = null
//    if (param != null && param.length > 2) {
    second.any = first
    second.other = param
//    } // to try next: separate if to two ifs with same condition

    val third = AnyHolder() // 93
//    anyUser(third)
    third.any = second
    third.other = null
    return third
}

class BinTree<T>(var value: T? = null, var left: BinTree<T>? = null, var right: BinTree<T>? = null)

fun <T> containsValue(value: Int, root: BinTree<T>?): Boolean {
    if (root == null) return false;
    if (root.value == value) return true
    return containsValue(value, root.left) || containsValue(value, root.right)
}

fun addToBst(value: Int, root: BinTree<Int>) {
    if (containsValue(value, root)) return
    if (root.value!! < value) {
        if (root.right == null) {
            val newTree = BinTree<Int>()
            newTree.value = value
            root.right = newTree
        } else addToBst(value, root.right!!)
    } else {
        if (root.left == null) {
            val newTree = BinTree<Int>()
            newTree.value = value
            root.left = newTree
        } else addToBst(value, root.left!!)
    }
}

class IntWrapper { var value by Delegates.notNull<Int>() }

fun addRangeToBinTree(start: Int, end: Int): BinTree<Int> {
    var root = BinTree<Int>()
    anyUser(root)
    root.value = start
    for (i in start..end) {
        if (anyUser(i)) {
            val newNode = BinTree<Int>()
            anyUser(newNode)
            newNode.value = i
            root.right = newNode
            root = newNode
        } else {
            val newNode = BinTree<Int>()
            anyUser(newNode)
            newNode.value = i
            root.left = newNode
            root = newNode
        }
    }
    return root
}

fun binTreeSimple(): BinTree<Nothing?> {
    val binTree = BinTree<Nothing?>()
    anyUser(binTree)
    binTree.value = null
    val binTree2 = BinTree<Nothing?>()
    anyUser(binTree2)
    binTree2.value = null
    binTree.left = binTree2
    val binTree3 = BinTree<Nothing?>()
    anyUser(binTree3)
    binTree3.value = null
    binTree.right = binTree3
    val binTree4 = BinTree<Nothing?>()
    anyUser(binTree4)
    binTree4.value = null
    binTree3.left = binTree4

    return binTree

}

fun binTreeCycleWithLoopUnrolling(): BinTree<Nothing?> {
    var root = BinTree<Nothing?>()
    anyUser(root)
    root.value = null
    for(i in 0..10) {
        val newNode = BinTree<Nothing?>()
        anyUser(newNode)
        newNode.value = null
        root.left = newNode
        root = newNode
    }
    return root
}

fun binTreeCycle(n: Int): BinTree<Nothing?> {
    var root = BinTree<Nothing?>()
    anyUser(root)
    val origRoot = root
    root.value = null
    for(i in 0..n) {
        val newNode = BinTree<Nothing?>()
        anyUser(newNode)
        newNode.value = null
        root.left = newNode
        root = newNode
    }
    return origRoot
}

class PointsToAnalysisTests {

//    @Test
//    fun `get graal graphs for anyHolder`() {
//        println("anyHolder graal graphs")
//        val methodToGraph = MethodToGraph()
//        val graph = methodToGraph.getCFG(::anyHolder.javaMethod)
//        val adapter = GraalAdapter.fromGraal(graph)
//        val writer = StringWriter()
//        adapter.export(writer)
//        println(writer.toString())
//        println()
//    }
//
    @Test
    fun `get pointsto graph of anyHolder`() {
        println("# anyHolder")
        val analysis = PointsToAnalysis(::anyHolder.javaMethod)
        analysis.printGraph()
        println()
        val graph = analysis.pointsToGraph
//        assert(graph.vertexSet().filter { it.isType("AllocatedObjectNode") }.size == 3)
    }

    @Test
    fun `get pointsto graph of anyHolderVariant`() {
        println("# anyHolderVariant")
        val analysis = PointsToAnalysis(::anyHolderVariant.javaMethod)
        analysis.printGraph()
        println()
        val graph = analysis.pointsToGraph
        assert(graph.vertexSet().filterIsInstance<AnalysisNode.IR>().filter { it.isType("AllocatedObjectNode") }.size == 3)
    }

    @Test
    fun `get pointsto graph of anyHolder2`() {
        println("# anyHolder2")
        val analysis = PointsToAnalysis(::anyHolder2.javaMethod)
        analysis.printGraph()
        println()
        val graph = analysis.pointsToGraph
        assert(graph.vertexSet().filterIsInstance<AnalysisNode.IR>().filter { it.isType("AllocatedObjectNode") }.size == 4)
    }

    @Test
    fun `get pointsto graph of anyHolder3`() {
        println("# anyHolder3")
        val analysis = PointsToAnalysis(::anyHolder3.javaMethod)
        analysis.printGraph()
        println()
        val graph = analysis.pointsToGraph
        assert(graph.vertexSet().filterIsInstance<AnalysisNode.IR>().filter { it.isType("AllocatedObjectNode") }.size == 3)
    }

    @Test
    fun `print addToBst`() {
        val m = MethodToGraph(false)
        val g = m.getAnalysisGraph(::anyHolder3.javaMethod)
        println(g.export())
    }

    @Test
    fun `get pointsto graph of addToBst`() {
        println("# addToBst")
        val analysis = PointsToAnalysis(::addToBst.javaMethod)
        analysis.printGraph()
        println()
        val graph = analysis.pointsToGraph
        assert(graph.vertexSet().filterIsInstance<AnalysisNode.IR>().filter { it.isType("AllocatedObjectNode") }.size == 2)
    }

//    @Test
//    fun `get pointsto graph of addRangeToBinTree`() {
//        println("addRangeToBinTree")
//        val analysis = PointsToAnalysis(::addRangeToBinTree.javaMethod)
//        analysis.printGraph()
//        println()
//        val graph = analysis.pointsToGraph
//        assert(graph.vertexSet().filter { it.isType("AllocatedObjectNode") }.size == 2)
//    }

    @Test
    fun `get pointsto graph of binTreeSimple`() {
        println("# binTreeSimple")
        val analysis = PointsToAnalysis(::binTreeSimple.javaMethod)
        analysis.printGraph()
        println()
        val graph = analysis.pointsToGraph
        assert(graph.vertexSet().filterIsInstance<AnalysisNode.IR>().filter { it.isType("AllocatedObjectNode") }.size == 4)
    }

    @Test
    fun `get pointsto graph of binTreeCycleWithLoopUnrolling`() {
        println("# binTreeCycleWithLoopUnrolling")
        val analysis = PointsToAnalysis(::binTreeCycleWithLoopUnrolling.javaMethod)
        analysis.printGraph()
        println()
        val graph = analysis.pointsToGraph
        assert(graph.vertexSet().filterIsInstance<AnalysisNode.IR>().filter { it.isType("AllocatedObjectNode") }.size == 12)
    }

    @Test
    fun `get graal graphs for binTreeCycle`() {
        println("# binTreeCycle graal graphs")
        val methodToGraph = MethodToGraph()
        val graph = methodToGraph.getCFG(::binTreeCycle.javaMethod)
        val adapter = GraalIRGraph.fromGraal(graph)
        val writer = StringWriter()
        adapter.exportQuery(writer)
        println(writer.toString())
        println()
    }

    @Test
    fun `get pointsto graph of binTreeCycle`() {
        println("# binTreeCycle")
        val analysis = PointsToAnalysis(::binTreeCycle.javaMethod)
        analysis.printGraph()
        println()
        val graph = analysis.pointsToGraph
        assert(3 == graph.vertexSet().filterIsInstance<AnalysisNode.IR>().filter { it.isType("AllocatedObjectNode") }.size)
    }

    @Test
    fun `get graal graphs for addRangeToBinTree`() {
        val methodToGraph = MethodToGraph()
        val graph = methodToGraph.getCFG(::binTreeCycle.javaMethod)
        val adapter = GraalIRGraph.fromGraal(graph)
        val writer = StringWriter()
        adapter.exportQuery(writer)
        println(writer.toString())
        println()
    }

}
