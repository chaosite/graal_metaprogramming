package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import kotlin.reflect.KProperty

typealias CaptureGroupAction<T> = (List<NodeWrapper>) -> T?
typealias WholeMatchAction = (Map<String, List<NodeWrapper>>) -> Unit
typealias GraphQueryMatch = Map<GraphQueryVertex<*>, List<NodeWrapper>>

abstract class QueryExecutor<T>(
    val graph: GraalAdapter,
    val initializer: () -> T,
    val dependencies: Map<Any, Map<String, Any>> = mapOf()
) {
    inner class MapProxy<K>(private val obj: MutableMap<K, T>) : MutableMap<K, T> by obj {
        override fun put(key: K, value: T): T? {
            if (!containsKey(key) || value != getValue(key)) {
                hasChanged = true
            }
            return obj.put(key, value)
        }

        override fun toString(): String {
            return obj.toString()
        }
    }

    private val _captureGroupActions = linkedMapOf<String, CaptureGroupAction<T>>()
    private val _wholeMatchActions = linkedMapOf<String, WholeMatchAction>()
    private val _queries = linkedMapOf<String, GraphQuery>()
    protected var hasChanged: Boolean = false
    open val captureGroupActions: Map<String, CaptureGroupAction<T>> get() = _captureGroupActions.toMap()
    open val wholeMatchActions: Map<String, WholeMatchAction> get() = _wholeMatchActions.toMap()
    open val queries: Map<String, GraphQuery> get() = _queries.toMap()
    open var nodeState: MutableMap<NodeWrapper, T> = MapProxy(hashMapOf<NodeWrapper, T>()).withDefault { initializer() }

    protected operator fun CaptureGroupAction<T>.provideDelegate(thisRef: QueryExecutor<T>, property: KProperty<*>) =
        also {
            _captureGroupActions[property.name] = this
        }

    protected operator fun CaptureGroupAction<T>.getValue(thisRef: QueryExecutor<T>, property: KProperty<*>) = this

    @JvmName("WholeMatchAction_provideDelegate")
    protected operator fun WholeMatchAction.provideDelegate(thisRef: QueryExecutor<T>, property: KProperty<*>) = also {
        _wholeMatchActions[property.name] = this
    }

    @JvmName("WholeMatchAction_getValue")
    protected operator fun WholeMatchAction.getValue(thisRef: QueryExecutor<T>, property: KProperty<*>) = this

    protected operator fun GraphQuery.provideDelegate(thisRef: QueryExecutor<T>, property: KProperty<*>) = also {
        _queries[property.name] = this
    }

    protected operator fun GraphQuery.getValue(thisRef: QueryExecutor<T>, property: KProperty<*>) = this

    /* TODO: Is this actually a good idea? */
    protected operator fun String.provideDelegate(thisRef: QueryExecutor<T>, property: KProperty<*>) = also {
        _queries[property.name] = GraphQuery.importQuery(this)
    }

    protected operator fun String.getValue(thisRef: QueryExecutor<T>, property: KProperty<*>) = this

    fun iterateUntilFixedPoint(limit: Int = 50): Map<NodeWrapper, T> {
        val matches: List<Pair<String, GraphQueryMatch>> = queries
            .flatMap { (name, query) -> query.match(graph).map { Pair(name, it) } }

        nodeState = MapProxy(hashMapOf<NodeWrapper, T>()).withDefault { initializer() }
        for (i in 0..limit) {
            hasChanged = false
            matches.asSequence().map(this::executeWithState).forEach(nodeState::putAll)
            if (!hasChanged)
                break

        }
        return nodeState
    }

    private fun executeWithState(namedMatch: Pair<String, GraphQueryMatch>): Map<NodeWrapper, T> {
        val (name, match) = namedMatch
        wholeMatchActions["${name}Action"]?.invoke(match.filter { it.key.captureGroup().isPresent }
            .mapKeys { it.key.captureGroup().get() })
        return execute(match)
    }

    private fun execute(match: GraphQueryMatch): Map<NodeWrapper, T> =
        match.entries.asSequence()
            .filter { it.key.captureGroup().isPresent && captureGroupActions.containsKey(it.key.captureGroup().get()) }
            .flatMap { (queryVertex, nodes) ->
                val ret = captureGroupActions[queryVertex.captureGroup().get()]!!.invoke(nodes)
                nodes.map { node -> Pair(node, ret) }
            }
            .filter { it.second != null }
            .filterIsInstance<Pair<NodeWrapper, T>>()
            .toMap()
}
