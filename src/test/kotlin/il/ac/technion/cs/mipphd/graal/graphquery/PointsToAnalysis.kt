package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.SourcePosTool
import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter
import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import org.graalvm.compiler.graph.Node
import org.graalvm.compiler.nodes.ValueNode
import org.graalvm.compiler.nodes.java.LoadFieldNode
import org.graalvm.compiler.nodes.java.StoreFieldNode
import org.graalvm.compiler.nodes.virtual.VirtualInstanceNode
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File
import java.io.StringWriter
import kotlin.random.Random
import kotlin.reflect.jvm.javaMethod

//data class A(val num: Int, val id: Int = 0) {
//    init {
//        println("test")
//    }
//
//}

fun f2(a: A?) {
    println(a?.num)
}
// query for all allocation sites (like mccarthy 91)
// find reads and writes (data edge)

fun one() = 1
fun two() = 2
fun fib(n: Int): Int = if (n in 1..2) 1 else fib(n - 1) + fib(n - 2)


val a1 = A(5)
val a2 = A(3)
val rng = Random.Default

data class Box(val a: A)

fun exampleFun(num1: Int, num2: Int): Boolean {
    val a1 = A(num1)
    val a2 = A(num2)
    val b = Box(a2)
    if (a1.num > b.a.num) {
        print("A")
        return true
    } else {
        print("B")
        return false
    }
}

fun part1(x: Int, y: Int): Pair<Box, Box> {
    return Box(A(x)) to Box(A(y))
}

fun part2(b1: Box, b2: Box) {
    if (b1.a.num < b2.a.num) {
        //
    } else {
        //
    }
}

fun complete(f1: (Int, Int) -> Pair<Box, Box>, f2: (Box, Box) -> Unit, x: Int, y: Int) {
    val (box1, box2) = f1(x, y)
    f2(box1, box2)
}

@Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
fun f1(l: MutableSet<Any>, num: Int): Int {
    val a1 = A(num)
    l.add(java.util.ArrayList<Int>())
    l.add(a1.num)
    l.add(a1)
//    val a2 = A(3)
//    if(fib(-1) == 5) {
//        f2(a1)
//        A(3)
//        return one()
//    } else {
//        f2(a2)
//        return two()
//    }
//    if(rng.nextBoolean()) {
//        f2(a1)
//    } else {
//        f2(a2)
//    }
//    f2(a1)
    return 0
}

// notes:
// allocations don't seem to work with CFG generators?
// graal is very smart in optimizations: only allocating things that are actually used and simplifying redundant
// expressions, even calling functions like fib() and of course functions like one() and two()
// getting it to actually generate a function call here required using values for which fib does not terminate
// values for which fib does terminate get translated to the actual result for fib
// this works in all circumstances where values are used (both in return values and in "if")
// also, constructors get inlined
// what is "Deopt" for a node? it's created all the time instead of actual code in many cases as simple as "f2(a1)"
// receiving parameters that are objects incurs a large overhead of nodes because of kotlin null-safety boilerplate


val forceAllocSet = mutableSetOf<Any?>()
fun allocatingFunction(x: Int, y: Int, z: Int) {
    val a = A(x)
    val b = A(y)
    val c = A(z)
    println(a.num)
    println(b.num)
    println(c.num)
    forceAllocSet.add(a)
    forceAllocSet.add(b)
    forceAllocSet.add(c)
}

fun aliasingFunction(x: Int, y: Int, z: Int) {
    var a = A(x)
    var b = A(y)
    var c = A(z)
    var temp = b
    b = a
    temp = a
    a = c
    c = temp
    forceAllocSet.add(a)
    forceAllocSet.add(b)
    forceAllocSet.add(c)
}

external fun anyUser(any: Any?): Boolean
data class AnyHolder(var any: Any? = null, var other: Any? = null) // { init { anyUser(this) } }

fun anyHolder(param: String?): AnyHolder {
    val first = AnyHolder(param ?: "")
    anyUser(first)
    val second = AnyHolder(first, param ?: "") // AnyHolder(if(anyUser(param)) first else param)
    anyUser(second)
    val third = AnyHolder(second)
    anyUser(third)
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

    return third
}

//fun accessingFunction(a: A, b: A, c: A) {
//    val num1 = a.num
//    val num2 = b.num
//    val num3 = c.num
//}

class GenericObjectWithField(val obj: NodeWrapper?, val field: String) : NodeWrapper(null) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GenericObjectWithField) return false
        if (obj != other.obj) return false
        if (field != other.field) return false
        return true
    }

    override fun hashCode(): Int {
        var result = obj.hashCode()
        result = 31 * result + field.hashCode()
        return result
    }

    override fun toString(): String {
        return "($obj, $field)"
    }

    override fun isType(className: String?): Boolean {
        return className == "GenericObjectWithField"
    }
}

internal class PointsToAnalysis {
    init {
//        val field = Class.forName("org.graalvm.compiler.graph.Node").getField("TRACK_CREATION_POSITION")
//        field.isAccessible = true
//        val modifiersField = Field::class.java.getDeclaredField("modifiers")
//        modifiersField.isAccessible = true
//        modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())
//        field.set(null, true);
    }

    @Nested
    @DisplayName("PointsToAnalysis")
    inner class PointsToAnalysisTests {
        private val firstMethod = ::f1.javaMethod
        private val secondMethod = ::f2.javaMethod
        private val methodToGraph = MethodToGraph()

        @Test
        fun `print f1 f2 graphs`() {
            println("----f1----")
            var cfg = methodToGraph.getCFG(::f1.javaMethod)
            var adapted = GraalAdapter.fromGraal(cfg)

            var sw = StringWriter()
            adapted.exportQuery(sw)

            println(sw.buffer)
            val f = File("out.dot")
            f.writeText(sw.buffer.toString())
            println("----f2----")
            cfg = methodToGraph.getCFG(secondMethod)
            adapted = GraalAdapter.fromGraal(cfg)
            sw = StringWriter()
            adapted.exportQuery(sw)

            println(sw.buffer)
            assertTrue(true)
        }

        @Test
        fun `print allocatingFunction graphs`() {
            val cfg = methodToGraph.getCFG(::allocatingFunction.javaMethod)
            val adapted = GraalAdapter.fromGraal(cfg)

            val sw = StringWriter()
            adapted.exportQuery(sw)

            println(sw.buffer)
        }

        @Test
        fun `get alloc nodes via graph executor`() {
//            println(Node.TRACK_CREATION_POSITION)
            val cfg = methodToGraph.getCFG(::anyHolder.javaMethod)
            val graph = GraalAdapter.fromGraal(cfg)
            val typeMethod =
                VirtualInstanceNode::class.java.getDeclaredMethod("type") // ()->ResolvedJavaType, escaping module issues
            val nameFromJavaTypeMethod = Class.forName("jdk.vm.ci.meta.JavaType").getDeclaredMethod("getName")
            val analyzer = object : QueryExecutor<String>(graph, { "" }) {
                val virtualQuery by """
digraph G {
    n [ label="(?P<virtual>)|is('VirtualInstanceNode')" ];
}
"""
                val virtual: CaptureGroupAction<String> by { nodes: List<NodeWrapper> ->
                    val node = nodes.first().node as VirtualInstanceNode
                    val stacktrace = SourcePosTool.getStackTraceElement(node)
                    val typeName = (nameFromJavaTypeMethod(typeMethod(node)) as String)
                        .removeSurrounding("L", ";")
                        .split("/").joinToString(".")
                    "allocation of $typeName at ${stacktrace.className}.${stacktrace.methodName}:${stacktrace.lineNumber}"
//                    (node.node as VirtualInstanceNode).creationPosition.toString()// objectId.toString() // object ID from cfg
                }
            }
            val results = analyzer.iterateUntilFixedPoint()
            val items = results.toList().asSequence().sortedBy { it.first.id }.map { it.second }
            for (item in items) {
                println(item)
            }
        }

        @Test
        fun `print accessingFunction graphs`() {
            val cfg = methodToGraph.getCFG(A::accessingFunction.javaMethod)
            val adapted = GraalAdapter.fromGraal(cfg)

            val sw = StringWriter()
            adapted.exportQuery(sw)

            println(sw.buffer)
        }

        @Test
        fun `get read write edges via graph executor`() {
            val method = ::complete.javaMethod // A::accessingFunction.javaMethod
            val cfg = methodToGraph.getCFG(method)
            val graph = GraalAdapter.fromGraal(cfg)
            val analyzer = object : QueryExecutor<String>(graph, { "" }) {
                // Change "query is not weakly connected" error to "missing edges in query" or something like that
                // Allow accessing of query results separately
                // A metafunction: length of kleene star path
                val loadQuery by """
digraph G {
	loadField [ label="(?P<loadfield>)|is('java.LoadFieldNode')" ];
    nop [ label="(?P<nop>)|is('PiNode')" ];
	value [ label="(?P<value>)|not is('PiNode')" ];

	value -> nop [ label="*|is('DATA') and name() = 'object'" ];
    nop -> loadField [ label="is('DATA')" ];
}
"""
                val loadQueryAction: WholeMatchAction by { captureGroups: Map<String, List<NodeWrapper>> ->
                    val loadField = captureGroups.getValue("loadfield").first()
                    val value = captureGroups.getValue("value").first()

                    // I'd use LoadFieldNode.field() for below but I can't get it to work because of module issues
                    fun getNameFromNode(node: NodeWrapper) =
                        (node.node as LoadFieldNode).toString().split("#")[1] // perfect code 10/10

                    //                    fun formatValueName(node: NodeWrapper): String {
//                        val paramId = (node.node as ValueNode).toString().split("|")[1].removeSurrounding("Parameter(", ")").toInt()
//                        return "parameter #$paramId"
//                    }
                    fun formatValueName(node: NodeWrapper) = (node.node as ValueNode).toString().split("|")[1]
                    state[loadField] = "Loading in ID ${loadField.id} value from ${formatValueName(value)} field name ${
                        getNameFromNode(loadField)
                    }"
                }

                val storeQuery by """
digraph G {
	storeField [ label="(?P<storefield>)|is('java.StoreFieldNode')" ];
	value [ label="(?P<value>)|1 = 1" ];

	value -> storeField [ label="is('DATA') and name() = 'value'" ];
}
"""
                val storeQueryAction: WholeMatchAction by { captureGroups: Map<String, List<NodeWrapper>> ->
                    val storeField = captureGroups.getValue("storefield").first()
                    val value = captureGroups.getValue("value").first()

                    // I'd use StoreFieldNode.field() for below but I can't get it to work because of module issues
                    fun getNameFromNode(node: NodeWrapper) =
                        (node.node as StoreFieldNode).toString().split("#")[1] // perfect code 10/10

                    fun formatValueName(node: NodeWrapper) = (node.node as ValueNode).toString().split("|")[1]
                    state[storeField] =
                        "Storing in ID ${storeField.id} value from ${formatValueName(value)} field name ${
                            getNameFromNode(storeField)
                        }"
                }
            }
            val results = analyzer.iterateUntilFixedPoint()
            val items = results.toList().sortedBy { it.first.id }.map { it.second }
            for (item in items) {
                println(item)
            }
        }

        /** Interesting:
         * public static final boolean TRACK_CREATION_POSITION = Boolean.parseBoolean(Services.getSavedProperties().get("debug.graal.TrackNodeCreationPosition"));
         * In Graal's org.graalvm.compiler.graph.Node.java
         * Enough to change jdk.internal.misc.VM.getSavedProperties()["debug.graal.TrackNodeCreationPosition"] to "true"
         */

        @Test
        fun `print 'complete' graph`() {
            val cfg = methodToGraph.getCFG(::complete.javaMethod)
            val adapted = GraalAdapter.fromGraal(cfg)

            val sw = StringWriter()
            adapted.exportQuery(sw)

            println(sw.buffer)
            assertTrue(true)
        }

        @Test
        fun `print anyHolder graphs`() {
            val cfg = methodToGraph.getCFG(::anyHolder.javaMethod)
            val adapted = GraalAdapter.fromGraal(cfg)

            val sw = StringWriter()
            adapted.exportQuery(sw)

            println(sw.buffer)
        }

        @Test
        fun `get pointsto graph preliminaries of anyHolder`() {
            val cfg = methodToGraph.getCFG(::anyHolder.javaMethod)
            val graph = GraalAdapter.fromGraal(cfg)
            val nopNodes = listOf(
                "Pi",
                "VirtualInstance",
                "ValuePhi",
                "Begin",
                "Merge",
                "End",
                "FrameState",
                "VirtualObjectState",
                "MaterializedObjectState"
            )
                .map { if (it.endsWith("State")) it else "${it}Node" }
            // actually, we do want to search for CommitAllocation and not VirtualInstance because CommitAllocation is where the parameters come in
            val constructorCallSiteQuery = object : QueryExecutor<MutableSet<NodeWrapper>>(graph, { mutableSetOf() }) {
                val virtualQuery by """
digraph G {
    commitAllocNode [ label="(?P<alloc>)|is('CommitAllocationNode')" ];
    nop [ label="(?P<nop>)|${nopNodes.joinToString(" or ") { "is('$it')" }}" ];
	value [ label="(?P<value>)|${nopNodes.joinToString(" and ") { "not is('$it')" }}" ];

	value -> nop [ label="*|is('DATA')" ];
    nop -> commitAllocNode [ label="is('DATA')" ];
}
""" // value -> nop [ label="*|is('DATA') and name() = 'object'" ];
                val virtualQueryAction: WholeMatchAction by { captureGroups: Map<String, List<NodeWrapper>> ->
                    state[captureGroups["alloc"]!!.first()]?.addAll(captureGroups["value"]!!)
                        ?: (captureGroups["value"]!!.toMutableSet()
                            .also { state[captureGroups["alloc"]!!.first()] = it })
                    Unit
                }
            }
            val results = constructorCallSiteQuery.iterateUntilFixedPoint()
            val items = results.toList().sortedBy { it.first.id }
            for (item in items) {
                println(item)
            }
            println()
            val associationQuery = object : QueryExecutor<NodeWrapper?>(graph, { null }) {
                val virtualQuery by """
digraph G {
    commitAllocNode [ label="(?P<alloc>)|is('CommitAllocationNode')" ];
	allocatedObjNode [ label="(?P<obj>)|is('AllocatedObjectNode')" ];

    commitAllocNode -> allocatedObjNode [ label="is('DATA')" ];
}
"""
                val virtualQueryAction: WholeMatchAction by { captureGroups: Map<String, List<NodeWrapper>> ->
                    state[captureGroups["obj"]!!.first()] = captureGroups["alloc"]!!.first()
                }
            }

            val associationResults = associationQuery.iterateUntilFixedPoint()
            val associationItems = associationResults.toList().sortedBy { it.first.id }
            for (item in associationItems) {
                println(item)
            }
            println()
            val associated =
                associationItems.associate { itt -> itt.first to items.first { it.first == itt.second }.second }
            for (item in associated) {
                println(item)
            }
        }

        // 3 sorts of nodes:
        // CommitAllocation - represents the beginning of the constructor
        // VirtualInstance - represents the end of the constructor
        // AllocatedObject - represents the allocated object itself

        @Test
        fun `get pointsto graph of anyHolder`() {
            val cfg = methodToGraph.getCFG(::anyHolder.javaMethod)
            val graph = GraalAdapter.fromGraal(cfg)
            // actually, we do want to search for CommitAllocation and not VirtualInstance because CommitAllocation is where the parameters come in
            val constructorCallSiteQuery = object : QueryExecutor<MutableSet<NodeWrapper>>(graph, { mutableSetOf() }) {
                val virtualQuery by """
digraph G {
    commitAllocNode [ label="(?P<alloc>)|is('CommitAllocationNode')" ];
    nop [ label="(?P<nop>)|is('PiNode') or is('VirtualInstanceNode')" ];
	value [ label="(?P<value>)|not is('PiNode') and not is('VirtualInstanceNode')" ];
    commitAllocOrig [ label="(?P<allocOrig>)|is('CommitAllocationNode')" ]; """ /* we need to make this and the next line optional */ + """

    commitAllocOrig -> value [ label="is('DATA') and name() = 'commit'" ];
	value -> nop [ label="*|is('DATA') and name() = 'object'" ];
    nop -> commitAllocNode [ label="is('DATA')" ];
}
"""
                val virtualQueryAction: WholeMatchAction by { captureGroups: Map<String, List<NodeWrapper>> ->
                    state[captureGroups["alloc"]!!.first()]?.addAll(captureGroups["allocOrig"]!!)
                        ?: (captureGroups["allocOrig"]!!.toMutableSet()
                            .also { state[captureGroups["alloc"]!!.first()] = it })
                    Unit
                }
            }
            val results = constructorCallSiteQuery.iterateUntilFixedPoint()
            val items = results.toList().sortedBy { it.first.id }
            for (item in items) {
                println(item)
            }

        }


        @Test
        fun `print anyHolder2 graphs`() {
            val cfg = methodToGraph.getCFG(::anyHolder2.javaMethod)
            val adapted = GraalAdapter.fromGraal(cfg)

            val sw = StringWriter()
            adapted.exportQuery(sw)

            val rawGraph = sw.buffer.toString().split("\n")
            val frameStateNodes = rawGraph.filter { it.contains("FrameState") }
                .map { it.trim().split(" ")[0] }.toSet()
            println(rawGraph.filter {
                "FrameState" !in it && frameStateNodes.all { itt -> " $itt -" !in it && "> $itt " !in it }
            }.joinToString("\n"))
        }

        @Test
        fun `get pointsto graph preliminaries of anyHolder2`() {
            val cfg = methodToGraph.getCFG(::anyHolder2.javaMethod)
            val graph = GraalAdapter.fromGraal(cfg)
            val nopNodes = listOf(
                "Pi",
                "VirtualInstance",
                "ValuePhi",
                "Begin",
                "Merge",
                "End",
                "FrameState",
                "VirtualObjectState",
                "MaterializedObjectState"
            ).map { if (it.endsWith("State")) it else "${it}Node" }

            val storeSiteQuery = object : QueryExecutor<MutableSet<NodeWrapper>>(graph, { mutableSetOf() }) {
                val storeQuery by """
digraph G {
    storeNode [ label="(?P<store>)|is('StoreFieldNode')" ];
    nop [ label="(?P<nop>)|${nopNodes.joinToString(" or ") { "is('$it')" }}" ];
	value [ label="(?P<value>)|${nopNodes.joinToString(" and ") { "not is('$it')" }}" ];

	value -> nop [ label="*|is('DATA')" ];
    nop -> storeNode [ label="name() = 'value'" ];
}
"""
                val storeQueryAction: WholeMatchAction by { captureGroups: Map<String, List<NodeWrapper>> ->
                    state[captureGroups["store"]!!.first()]?.addAll(captureGroups["value"]!!)
                        ?: (captureGroups["value"]!!.toMutableSet()
                            .also { state[captureGroups["store"]!!.first()] = it })
                    Unit
                }
            }
            val results = storeSiteQuery.iterateUntilFixedPoint()
            val items = results.toList().sortedBy { it.first.id }
            for (item in items) {
                println(item)
            }
            println()
            val fieldAssociationQuery = object : QueryExecutor<NodeWrapper?>(graph, { null }) {
                val assocQuery by """
digraph G {
    storeNode [ label="(?P<store>)|is('StoreFieldNode') or is('LoadFieldNode')" ];
	value [ label="(?P<value>)|is('AllocatedObjectNode')" ];

	value -> storeNode [ label="name() = 'object'" ];
}
"""
                val assocQueryAction: WholeMatchAction by { captureGroups: Map<String, List<NodeWrapper>> ->
                    state[captureGroups["store"]!!.first()] = captureGroups["value"]!!.first()
                }
            }
            val resultsFieldAssoc = fieldAssociationQuery.iterateUntilFixedPoint()
            val itemsFieldAssoc =
                resultsFieldAssoc.toList().sortedBy { it.first.id }.filter { it.first.node is StoreFieldNode }
            for (item in itemsFieldAssoc) {
                println(item)
            }
            println()
            val clazz = Class.forName("org.graalvm.compiler.nodes.java.AccessFieldNode")
            val fieldMethod = clazz.getDeclaredMethod("field")
            val fieldClazz = Class.forName("jdk.vm.ci.meta.JavaField")
            val fieldNameMethod = fieldClazz.getDeclaredMethod("getName")
            val associated =
                itemsFieldAssoc.associate { itt ->
                    val key = GenericObjectWithField(itt.second, fieldNameMethod(fieldMethod(itt.first.node)) as String)
                    val value = mutableListOf<NodeWrapper>()
                    for (node in (items.firstOrNull { it.first == itt.first }?.second ?: listOf())) {
                        if (node.node is LoadFieldNode) {
                            value.add(
                                GenericObjectWithField(
                                    resultsFieldAssoc.toList().first { it.first == node }.second,
                                    fieldNameMethod(fieldMethod(node.node)) as String
                                )
                            )
                        } else value.add(node)
                    }
                    key to value
                }
            for (item in associated) {
                println(item)
            } // todo next: figure out why it sometimes adds itself to references
        }

        @Test
        fun `get pointsto graph of anyHolder2`() {
            val cfg = methodToGraph.getCFG(::anyHolder2.javaMethod)
            val graph = GraalAdapter.fromGraal(cfg)
            val nopNodes = listOf(
                "Pi",
                "VirtualInstance",
                "ValuePhi",
                "Begin",
                "Merge",
                "End",
                "FrameState",
                "VirtualObjectState",
                "MaterializedObjectState"
            ).map { if (it.endsWith("State")) it else "${it}Node" }

            val storeSiteQuery = object : QueryExecutor<MutableSet<NodeWrapper>>(graph, { mutableSetOf() }) {
                val storeQuery by """
digraph G {
    storeNode [ label="(?P<store>)|is('StoreFieldNode')" ];
    nop [ label="(?P<nop>)|${nopNodes.joinToString(" or ") { "is('$it')" }}" ];
	value [ label="(?P<value>)|${nopNodes.joinToString(" and ") { "not is('$it')" }}" ];

	value -> nop [ label="*|is('DATA') and not (name() = 'object')" ];
    nop -> storeNode [ label="name() = 'value'" ];
}
"""
                val storeQueryAction: WholeMatchAction by { captureGroups: Map<String, List<NodeWrapper>> ->
                    state[captureGroups["store"]!!.first()]?.addAll(captureGroups["value"]!!)
                        ?: (captureGroups["value"]!!.toMutableSet()
                            .also { state[captureGroups["store"]!!.first()] = it })
                    Unit
                }
            }
            val results = storeSiteQuery.iterateUntilFixedPoint()
            val items = results.toList().sortedBy { it.first.id }

            val fieldAssociationQuery = object : QueryExecutor<NodeWrapper?>(graph, { null }) {
                val assocQuery by """
digraph G {
    storeNode [ label="(?P<store>)|is('StoreFieldNode') or is('LoadFieldNode')" ];
	value [ label="(?P<value>)|is('AllocatedObjectNode')" ];

	value -> storeNode [ label="name() = 'object'" ];
}
"""
                val assocQueryAction: WholeMatchAction by { captureGroups: Map<String, List<NodeWrapper>> ->
                    state[captureGroups["store"]!!.first()] = captureGroups["value"]!!.first()
                }
            }
            val resultsFieldAssoc = fieldAssociationQuery.iterateUntilFixedPoint()
            val itemsFieldAssoc =
                resultsFieldAssoc.toList().sortedBy { it.first.id }.filter { it.first.node is StoreFieldNode }

            val clazz = Class.forName("org.graalvm.compiler.nodes.java.AccessFieldNode")
            val fieldMethod = clazz.getDeclaredMethod("field")
            val fieldClazz = Class.forName("jdk.vm.ci.meta.JavaField")
            val fieldNameMethod = fieldClazz.getDeclaredMethod("getName")
            val associated = itemsFieldAssoc.associate { itt ->
                val key = GenericObjectWithField(itt.second, fieldNameMethod(fieldMethod(itt.first.node)) as String)
                val value = mutableListOf<NodeWrapper>()
                for (node in (items.firstOrNull { it.first == itt.first }?.second ?: listOf())) {
                    if (node.node is LoadFieldNode) {
                        value.add(
                            GenericObjectWithField(
                                resultsFieldAssoc.toList().first { it.first == node }.second,
                                fieldNameMethod(fieldMethod(node.node)) as String
                            )
                        )
                    } else value.add(node)
                }
                key to value
            }
            var i = 1
            val nodes = associated.flatMap { it.value }.toSet().union(associated.keys).associateWith { i++ }
            val edges = mutableListOf<Pair<NodeWrapper, NodeWrapper>>()
            associated.forEach { item ->
                edges.addAll(item.value.map { item.key to it })
            }
            val edgesStrings = edges.map { (from, to) ->
                val fromId = nodes[from]!!
                val toId = nodes[to]!!
                "$fromId -> $toId"
            }
            val graphFormat = """
digraph G {
${nodes.entries.joinToString("\n") { "    ${it.value} [label=\"${it.key.toString().replace("\"", "'")}\"];" }}
${edgesStrings.joinToString("\n") { "    $it [ color=\"${if(edgesStrings.count { itt-> itt.split(" -> ")[0] == it.split(" -> ")[0] } == 1) "blue" else "red"}\" ];" }}
}
            """
            println(graphFormat)

        }
    }
}