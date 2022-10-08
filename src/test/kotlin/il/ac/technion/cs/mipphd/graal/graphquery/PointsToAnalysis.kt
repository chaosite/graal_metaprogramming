package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import org.graalvm.compiler.nodes.ValueNode
import org.graalvm.compiler.nodes.java.LoadFieldNode
import org.graalvm.compiler.nodes.java.StoreFieldNode
import org.jgrapht.alg.drawing.model.Points

class PointsToResult(var correspondingAllocatedObject: NodeWrapper? = null, val storedValues: MutableSet<NodeWrapper> = mutableSetOf())
class PointsToAnalysis(graph: GraalAdapter) : QueryExecutor<PointsToResult>(graph, { PointsToResult() }) {
    companion object {
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
	allocated [ label="(?P<value>)|is('AllocatedObjectNode')" ];

	allocated -> storeNode [ label="name() = 'object'" ];
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

    fun getPointsToGraphPreliminiaries() : PointsToGraphPreliminiaries {
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
        return associated
    }

    fun getPointsToGraph() : PointsToGraph {
        val associated = getPointsToGraphPreliminiaries()
        val nodes = associated.flatMap { it.value }.toSet().union(associated.keys.map { it.obj!! }).filter { it !is GenericObjectWithField }.toSet()
        val edges = mutableSetOf<Triple<NodeWrapper, String, NodeWrapper>>()
        associated.forEach { item ->
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
        return nodes to edges
    }

}

typealias PointsToGraph = Pair<Set<NodeWrapper>, Set<Triple<NodeWrapper, String, NodeWrapper>>>
typealias PointsToGraphPreliminiaries = Map<GenericObjectWithField, List<NodeWrapper>> // this definitely needs to be its own class, or maybe removed. TODO

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