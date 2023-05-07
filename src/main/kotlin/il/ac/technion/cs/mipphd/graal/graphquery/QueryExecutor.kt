package il.ac.technion.cs.mipphd.graal.graphquery

import kotlin.reflect.KProperty

private typealias CaptureGroupAction<T> = (List<AnalysisNode>) -> T?
private typealias CGAItem<T> = Pair<String, CaptureGroupAction<T>>
private typealias CaptureGroupActions<T> = Map<String, CaptureGroupAction<T>>
private typealias WholeMatchAction = (Map<String, List<AnalysisNode>>) -> Unit
private typealias GraphQueryMatch = Map<GraphQueryVertex, List<AnalysisNode>>

data class WholeMatchQuery(val query: GraphQuery, val action: WholeMatchAction) {
    constructor(query: String, action: WholeMatchAction) : this(GraphQuery.importQuery(query), action)
}

data class CaptureGroupQuery<T>(val query: GraphQuery, val action: CaptureGroupActions<T>) {
    companion object {
        fun <T> associate(a: Array<out CGAItem<T>>) = a.associate { it }
    }

    constructor(query: GraphQuery, vararg actions: CGAItem<T>) : this(query, associate(actions))

    constructor(query: String, vararg actions: CGAItem<T>) : this(GraphQuery.importQuery(query), associate(actions))
}

abstract class QueryExecutor<T>(
    val graph: AnalysisGraph,
    val initializer: () -> T,
    val dependencies: Map<Any, Map<String, Any>> = mapOf()
) {
    protected inline fun <reified T : AnalysisNode.Specific> singleAttachToNode(
        source: AnalysisNode,
        target: T,
        edge: AnalysisEdge.Extra
    ): Boolean {
        val oldTarget = graph.outgoingEdgesOf(source).map(graph::getEdgeTarget).filterIsInstance<T>().firstOrNull()
        var changed = false
        if (oldTarget != null && oldTarget != target) {
            graph.removeEdge(graph.getEdge(source, oldTarget))
            if (graph.inDegreeOf(oldTarget) == 0)
                graph.removeVertex(oldTarget)
            changed = true
        }
        if (oldTarget == null || oldTarget != target) {
            if (!graph.containsVertex(target))
                graph.addVertex(target)
            graph.addEdge(source, target, edge)
            changed = true
        }
        hasChanged = hasChanged || changed
        return changed
    }

    inner class MapProxy<K>(private val obj: MutableMap<K, T>) : MutableMap<K, T> by obj {
        override fun put(key: K, value: T): T? {
            if (!containsKey(key) || value != getValue(key)) {
                //hasChanged = true
            }
            return obj.put(key, value)
        }

        override fun toString(): String {
            return obj.toString()
        }
    }

    private val _captureGroupActions = linkedMapOf<String, CaptureGroupActions<T>>()
    private val _wholeMatchActions = linkedMapOf<String, WholeMatchAction>()
    private val _queries = linkedMapOf<String, GraphQuery>()
    protected var hasChanged: Boolean = false
    open val captureGroupActions: Map<String, CaptureGroupActions<T>> get() = _captureGroupActions.toMap()
    open val wholeMatchActions: Map<String, WholeMatchAction> get() = _wholeMatchActions.toMap()
    open val queries: Map<String, GraphQuery> get() = _queries.toMap()
    open var state: MutableMap<AnalysisNode, T> = MapProxy(hashMapOf<AnalysisNode, T>()).withDefault { initializer() }

    protected operator fun CaptureGroupQuery<T>.provideDelegate(thisRef: QueryExecutor<T>, property: KProperty<*>) =
        also {
            _queries[property.name] = this.query
            _captureGroupActions[property.name] = this.action
        }

    protected operator fun CaptureGroupQuery<T>.getValue(thisRef: QueryExecutor<T>, property: KProperty<*>) = this

    @JvmName("WholeMatchQuery_provideDelegate")
    protected operator fun WholeMatchQuery.provideDelegate(thisRef: QueryExecutor<T>, property: KProperty<*>) = also {
        _queries[property.name] = this.query
        _wholeMatchActions[property.name] = this.action
    }

    @JvmName("WholeMatchQuery_getValue")
    protected operator fun WholeMatchQuery.getValue(thisRef: QueryExecutor<T>, property: KProperty<*>) = this

    private fun runQueries() = queries
        .flatMap { (name, query) -> query.match(graph).map { Pair(name, it) } }

    fun iterateUntilFixedPoint(limit: Int = 50): Map<AnalysisNode, T> {
        state = MapProxy(hashMapOf<AnalysisNode, T>()).withDefault { initializer() }
        for (i in 0..limit) {
            hasChanged = false
            runQueries().asSequence().map(this::execute).forEach(state::putAll)
            if (!hasChanged)
                break

        }
        return state
    }

    private fun execute(namedMatch: Pair<String, GraphQueryMatch>): Map<AnalysisNode, T> {
        val (name, match) = namedMatch
        wholeMatchActions[name]?.invoke(match.filter { it.key.captureGroup().isPresent }
            .mapKeys { it.key.captureGroup().get() })
        return match.entries.asSequence()
            .filter {
                it.key.captureGroup().isPresent && (captureGroupActions[name]?.containsKey(
                    it.key.captureGroup().get()
                ) ?: false)
            }
            .flatMap { (queryVertex, nodes) ->
                // queryVertex.captureGroup().get()
                val ret = captureGroupActions[name]!![queryVertex.captureGroup().get()]!!.invoke(nodes)
                nodes.map { node -> Pair(node, ret) }
            }
            .filterIsInstance<Pair<AnalysisNode, T>>()
            .toMap()
    }
}
