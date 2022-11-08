package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.SourcePosTool
import il.ac.technion.cs.mipphd.graal.utils.EdgeWrapper
import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter
import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import org.graalvm.compiler.nodes.ValueNode
import org.graalvm.compiler.nodes.java.LoadFieldNode
import org.graalvm.compiler.nodes.java.StoreFieldNode
import org.jgrapht.nio.Attribute
import org.jgrapht.nio.DefaultAttribute
import org.jgrapht.nio.dot.DOTExporter
import java.io.StringWriter
import java.lang.reflect.Method

class PointsToResult(var correspondingAllocatedObject: NodeWrapper? = null, val storedValues: MutableSet<NodeWrapper> = mutableSetOf())
class PointsToAnalysis(val method: Method?)
    : QueryExecutor<PointsToResult>(GraalAdapter.fromGraal(methodToGraph.getCFG(method!!)), { PointsToResult() }) {
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
            EdgeWrapper.DATA to "blue",
            EdgeWrapper.CONTROL to "red",
            EdgeWrapper.ASSOCIATED to "black"
        )
        private val edgeStyle = mapOf(
            EdgeWrapper.DATA to "",
            EdgeWrapper.CONTROL to "",
            EdgeWrapper.ASSOCIATED to "dashed"
        )
        private fun writeQueryInternal(graalph: GraalAdapter, output: StringWriter) {
            val exporter = DOTExporter<NodeWrapper, EdgeWrapper> { v: NodeWrapper ->
                val node = v.node
                val suffix ="" // if (node is ValueNode) " (${SourcePosTool.getStackTraceElement(node)})" else "" // todo throws exception?
                v.node.id.toString() + suffix
            }

            exporter.setVertexAttributeProvider { v: NodeWrapper ->
                val attrs: MutableMap<String, Attribute> =
                    HashMap()
                attrs["label"] = DefaultAttribute.createAttribute(v.node.toString())
                attrs
            }

            exporter.setEdgeAttributeProvider { e: EdgeWrapper ->
                val attrs: MutableMap<String, Attribute> =
                    HashMap()
                attrs["label"] = DefaultAttribute.createAttribute(e.name)
                attrs["color"] = DefaultAttribute.createAttribute(edgeColor[e.label])
                attrs["style"] = DefaultAttribute.createAttribute(edgeStyle[e.label])
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
""") { captureGroups: Map<String, List<NodeWrapper>> ->
        if(state[captureGroups["store"]!!.first()] != null)
            state[captureGroups["store"]!!.first()]!!.storedValues.addAll(captureGroups["value"]!!).let { }
        else
            state[captureGroups["store"]!!.first()] = PointsToResult(null, captureGroups["value"]!!.toMutableSet())
    }
    val assocQuery by WholeMatchQuery("""
digraph G {
    storeNode [ label="(?P<store>)|is('StoreFieldNode') or is('LoadFieldNode')" ];
    nop [ label="(?P<nop>)|${NOP_NODES.joinToString(" or ") { "is('$it')" }}" ];
	allocated [ label="(?P<value>)|is('AllocatedObjectNode')" ];

	allocated -> nop [ label="*|is('DATA')" ];
    nop -> storeNode [ label="name() = 'object'" ];
}
""") { captureGroups: Map<String, List<NodeWrapper>> ->
        if(state[captureGroups["store"]!!.first()]?.correspondingAllocatedObject == null) {
            if (state[captureGroups["store"]!!.first()] != null)
                state[captureGroups["store"]!!.first()]!!.correspondingAllocatedObject =
                    captureGroups["value"]?.first()
            else
                state[captureGroups["store"]!!.first()] = PointsToResult(captureGroups["value"]?.first())
        }
    }


    fun getPointsToGraphPreliminiariesForDebug() : Triple<List<Pair<NodeWrapper, Set<NodeWrapper>>>,
            List<Pair<NodeWrapper, NodeWrapper?>>, PointsToGraphPreliminiaries> {
        val results = iterateUntilFixedPoint().toList().sortedBy { it.first.id }

        val associated = results.filter { it.first.node is StoreFieldNode }.associate { itt ->
            val key = GenericObjectWithField(itt.second.correspondingAllocatedObject, getFieldNameMethod(getFieldMethod(itt.first.node)) as String)
            val value = mutableListOf<NodeWrapper>()
            for (node in (results.firstOrNull { it.first == itt.first }?.second?.storedValues ?: listOf())) {
                if (node.node is LoadFieldNode) {
                    value.add(
                        GenericObjectWithField(
                            results.toList().first { it.first == node }.second.correspondingAllocatedObject,
                            getFieldNameMethod(getFieldMethod(node.node)) as String
                        )
                    )
                } else value.add(node)
            }
            key to value
        }
        return Triple(results.map { it.first to it.second.storedValues }, results.map { it.first to it.second.correspondingAllocatedObject }, associated)
    }


    val pointsToGraph : GraalAdapter by lazy {
        val results = iterateUntilFixedPoint().toList().sortedBy { it.first.id }

        val associated = results.filter { it.first.node is StoreFieldNode }.associate { itt ->
            val key = GenericObjectWithField(itt.second.correspondingAllocatedObject, getFieldNameMethod(getFieldMethod(itt.first.node)) as String)
            val value = mutableListOf<NodeWrapper>()
            for (node in (results.firstOrNull { it.first == itt.first }?.second?.storedValues ?: listOf())) {
                if (node.node is LoadFieldNode) {
                    value.add(
                        GenericObjectWithField(
                            results.toList().first { it.first == node }.second.correspondingAllocatedObject,
                            getFieldNameMethod(getFieldMethod(node.node)) as String
                        )
                    )
                } else value.add(node)
            }
            key to value
        }
        val nodes = associated.flatMap { it.value }.toSet().union(associated.keys.mapNotNull { it.obj })
            .filter { it !is GenericObjectWithField }.toSet()
        val edges = mutableSetOf<Triple<NodeWrapper, String, NodeWrapper>>()
        associated.filter { it.key.obj != null }.forEach { item ->
            edges.addAll(item.value.map { Triple(item.key.obj!!, item.key.field, it) })
        }
        while(true) {
            val toRemove = mutableSetOf<Triple<NodeWrapper, String, NodeWrapper>>()
            val toAdd = mutableSetOf<Triple<NodeWrapper, String, NodeWrapper>>()
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
        val graph = GraalAdapter()
        nodes.forEach(graph::addVertex)
        edges.forEach { graph.addEdge(it.first, it.third, EdgeWrapper(EdgeWrapper.ASSOCIATED, it.second)) }
        graph
    }

    fun printGraph() {
        val sw = StringWriter()
//        pointsToGraph.exportQuery(sw)
        writeQueryInternal(pointsToGraph, sw)
        println(sw.buffer)
    }

}

typealias PointsToGraphPreliminiaries = Map<GenericObjectWithField, List<NodeWrapper>>

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