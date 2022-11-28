package il.ac.technion.cs.mipphd.graal.graphquery.pointsto

import il.ac.technion.cs.mipphd.graal.graphquery.*
import il.ac.technion.cs.mipphd.graal.utils.WrappedIREdge
import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import org.graalvm.compiler.nodes.java.LoadFieldNode
import org.graalvm.compiler.nodes.java.StoreFieldNode
import org.jgrapht.nio.Attribute
import org.jgrapht.nio.DefaultAttribute
import org.jgrapht.nio.dot.DOTExporter
import java.io.StringWriter
import java.lang.reflect.Method

class PointsToResult(val correspondingAllocatedObjects: MutableSet<AnalysisNode> = mutableSetOf(),
                     val storedValues: MutableSet<AnalysisNode> = mutableSetOf(),
                     var commitAllocationAssociation: AnalysisNode? = null)
class PointsToAnalysis(graph: AnalysisGraph)
    : QueryExecutor<PointsToResult>(graph, { PointsToResult() }) {
    companion object {
        private val methodToGraph = MethodToGraph()
        val NOP_NODES = listOf(
            "Pi",
            "VirtualInstance",
            "ValuePhi",
            "VirtualObjectState",
            "MaterializedObjectState"
        ).map { if (it.endsWith("State")) it else "${it}Node" }
        val NOT_VALUE_NODES = listOf(
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

        private val accessFieldNodeClass = Class.forName("org.graalvm.compiler.nodes.java.AccessFieldNode")
        private val getFieldMethod = accessFieldNodeClass.getDeclaredMethod("field")
        private val fieldClazz = Class.forName("jdk.vm.ci.meta.JavaField")
        private val getFieldNameMethod = fieldClazz.getDeclaredMethod("getName")


        // todo - code duplication w/ GraalAdapter
        private val edgeColor = mapOf(
            WrappedIREdge.DATA to "blue",
            WrappedIREdge.CONTROL to "red",
            WrappedIREdge.ASSOCIATED to "black"
        )
        private val edgeStyle = mapOf(
            WrappedIREdge.DATA to "",
            WrappedIREdge.CONTROL to "",
            WrappedIREdge.ASSOCIATED to "dashed"
        )
        private fun writeQueryInternal(graalph: AnalysisGraph, output: StringWriter) {
            val exporter = DOTExporter<AnalysisNode, AnalysisEdge> { v: AnalysisNode ->
                val suffix ="" // if (node is ValueNode) " (${SourcePosTool.getStackTraceElement(node)})" else "" // todo throws exception?
                v.index.toString() + suffix
            }

            exporter.setVertexAttributeProvider { v: AnalysisNode ->
                val attrs: MutableMap<String, Attribute> =
                    HashMap()
                attrs["label"] = DefaultAttribute.createAttribute(v.toString())
                attrs
            }

            exporter.setEdgeAttributeProvider { e: AnalysisEdge ->
                val attrs: MutableMap<String, Attribute> =
                    HashMap()
                attrs["label"] = DefaultAttribute.createAttribute(e.label)
                attrs["color"] = DefaultAttribute.createAttribute("") // TODO: edgeColor[e.kind])
                attrs["style"] = DefaultAttribute.createAttribute("") // TODO: edgeStyle[e.kind])
                attrs
            }
            exporter.exportGraph(graalph, output)
        }
    }
    val storeQuery by WholeMatchQuery("""
digraph G {
    storeNode [ label="(?P<store>)|is('StoreFieldNode')" ];
    nop [ label="(?P<nop>)|${NOP_NODES.joinToString(" or ") { "is('$it')" }}" ];
	value [ label="(?P<value>)|${NOT_VALUE_NODES.joinToString(" and ") { "not is('$it')" }}" ];

	value -> nop [ label="*|is('DATA')" ];
    nop -> storeNode [ label="name() = 'value'" ];
}
""") { captureGroups ->
        if(state[captureGroups["store"]!!.first()] != null)
            state[captureGroups["store"]!!.first()]!!.storedValues.addAll(captureGroups["value"]!!).let { }
        else
            state[captureGroups["store"]!!.first()] = PointsToResult(mutableSetOf(), captureGroups["value"]!!.toMutableSet())
    }
    val assocQuery by WholeMatchQuery("""
digraph G {
    storeNode [ label="(?P<store>)|is('StoreFieldNode') or is('LoadFieldNode')" ];
    nop [ label="(?P<nop>)|${NOP_NODES.joinToString(" or ") { "is('$it')" }}" ];
	allocated [ label="(?P<value>)|is('AllocatedObjectNode')" ];

	allocated -> nop [ label="*|is('DATA')" ];
    nop -> storeNode [ label="name() = 'object'" ];
}
""") { captureGroups ->
            if (state[captureGroups["store"]!!.first()] != null)
                state[captureGroups["store"]!!.first()]!!.correspondingAllocatedObjects.addAll(captureGroups["value"] ?: mutableSetOf())
            else
                state[captureGroups["store"]!!.first()] =
                    PointsToResult(captureGroups["value"]?.toMutableSet() ?: mutableSetOf(), mutableSetOf())
    }

    val commitAllocQuery by WholeMatchQuery("""
digraph G {
    commit [ label="(?P<store>)|is('CommitAllocationNode')" ];
	alloc [ label="(?P<value>)|is('AllocatedObjectNode')" ];

	commit -> alloc [ label="name() = 'commit'" ];
}
""") { captureGroups ->
        if (state[captureGroups["alloc"]!!.first()] != null)
            state[captureGroups["alloc"]!!.first()]!!.commitAllocationAssociation = captureGroups["commit"]!!.first()
        else
            state[captureGroups["alloc"]!!.first()] =
                PointsToResult(mutableSetOf(), mutableSetOf(), captureGroups["commit"]!!.first())
    }

    constructor(method: Method?) : this(methodToGraph.getAnalysisGraph(method!!))

    fun getPointsToGraphPreliminiariesForDebug() : Triple<List<Pair<AnalysisNode, Set<AnalysisNode>>>,
            List<Pair<AnalysisNode, AnalysisNode?>>, PointsToGraphPreliminiaries> {
        val results = iterateUntilFixedPoint().toList().sortedBy { it.first.index }

        val associated = results.filterIsInstance<Pair<AnalysisNode.IR, PointsToResult>>().filter { it.first.node() is StoreFieldNode }.flatMap { pair ->
            pair.second.correspondingAllocatedObjects.filterIsInstance<AnalysisNode.IR>().map {
                Triple(GenericObjectWithField(it, getFieldNameMethod(getFieldMethod(it)) as String), it, pair)
            }
        }.associate { (key, alloc, itt) ->
            val value = mutableListOf<AnalysisNode>()
            for (node in (results.firstOrNull { it.first == itt.first }?.second?.storedValues?.filterIsInstance<AnalysisNode.IR>() ?: listOf())) {
                if (node.node() is LoadFieldNode) {
                    value.add(
                        GenericObjectWithField(
                            alloc,
                            getFieldNameMethod(getFieldMethod(node.node())) as String
                        )
                    )
                } else value.add(node)
            }
            key to value
        }
        return Triple(results.map { it.first to it.second.storedValues }, results.flatMap {
            it.second.correspondingAllocatedObjects.map { itt-> it.first to itt } }, associated)
    }


    val pointsToGraph : AnalysisGraph by lazy {
        val results = iterateUntilFixedPoint().toList().sortedBy { it.first.index }

        val associated = results.filterIsInstance<Pair<AnalysisNode.IR, PointsToResult>>().filter { it.first.node() is StoreFieldNode }.flatMap { pair ->
            pair.second.correspondingAllocatedObjects.map {
                Triple(GenericObjectWithField(it as AnalysisNode.IR, getFieldNameMethod(getFieldMethod(pair.first.node())) as String), it, pair)
            }
        }.associate { (key, alloc, itt) ->
            val value = mutableListOf<AnalysisNode>()
            for (node in (results.firstOrNull { it.first == itt.first }?.second?.storedValues?.filterIsInstance<AnalysisNode.IR>() ?: listOf())) {
                if (node.node() is LoadFieldNode) {
                    value.add(
                        GenericObjectWithField(
                            alloc,
                            getFieldNameMethod(getFieldMethod(node.node())) as String
                        )
                    )
                } else value.add(node)
            }
            key to value
        }
        val nodes = associated.flatMap { it.value }.toSet().union(associated.keys.mapNotNull { it.obj })
            .filter { it !is GenericObjectWithField }.toSet()
        val edges = mutableSetOf<Triple<AnalysisNode, String, AnalysisNode>>()
        associated.filter { it.key.obj != null }.forEach { item ->
            edges.addAll(item.value.map { Triple(item.key.obj!!, item.key.field, it) })
        }
        while(true) {
            val toRemove = mutableSetOf<Triple<AnalysisNode, String, AnalysisNode>>()
            val toAdd = mutableSetOf<Triple<AnalysisNode, String, AnalysisNode>>()
            for ((from, field, to) in edges) {
                if (to is GenericObjectWithField) {
                    toRemove.add(Triple(from, field, to))

                    toAdd.addAll(associated[to]!!.map { Triple(from, field, it) })
                }
            }
            if(toRemove.isEmpty() && toAdd.isEmpty()) break
            edges.removeAll(toRemove)
            edges.addAll(toAdd)
        }
        val graph = AnalysisGraph()
        nodes.forEach(graph::addVertex)
        edges.forEach { graph.addEdge(it.first, it.third,
            AnalysisEdge.Association(it.second)
        ) }
        graph
    }

    fun printGraph() {
        val sw = StringWriter()
//        pointsToGraph.exportQuery(sw)
        writeQueryInternal(pointsToGraph, sw)
        println(sw.buffer)
    }

}

typealias PointsToGraphPreliminiaries = Map<GenericObjectWithField, List<AnalysisNode>>

class GenericObjectWithField(val obj: AnalysisNode.IR?, val field: String) : AnalysisNode.Specific() {
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

    fun isType(className: String?): Boolean {
        return className == "GenericObjectWithField"
    }
}