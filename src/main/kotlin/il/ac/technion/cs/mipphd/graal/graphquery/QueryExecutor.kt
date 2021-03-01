package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import kotlin.reflect.KProperty

typealias MapFunc<T> = (List<NodeWrapper>) -> T?
typealias GraphQueryMatch = Map<GraphQueryVertex<*>, List<NodeWrapper>>
typealias MatchAndState<T> = Pair<GraphQueryMatch, MutableMap<String, T>>

abstract class QueryExecutor<T>(
        val graph: GraalAdapter,
        val initializer: () -> T,
        val dependencies: Map<Any, Map<String, Any>> = mapOf()
) {
    inner class Result(val groups: List<Map<String, T>>, val nodes: Map<NodeWrapper, T>)
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

    private val _functions = linkedMapOf<String, MapFunc<T>>()
    private val _queries = linkedSetOf<GraphQuery>()
    protected var hasChanged: Boolean = false
    open val functions: Map<String, MapFunc<T>> get() = _functions.toMap()
    open var state: MutableMap<String, T> = MapProxy(hashMapOf<String, T>()).withDefault { initializer() }
    open var nodeState: MutableMap<NodeWrapper, T> = MapProxy(hashMapOf<NodeWrapper, T>()).withDefault { initializer() }

    protected operator fun MapFunc<T>.provideDelegate(thisRef: QueryExecutor<T>, property: KProperty<*>) = also {
        _functions[property.name] = { n -> this(n).also { if (it != null) state[property.name] = it } }
    }

    protected operator fun MapFunc<T>.getValue(thisRef: QueryExecutor<T>, property: KProperty<*>) = this

    protected operator fun GraphQuery.provideDelegate(thisRef: QueryExecutor<T>, property: KProperty<*>) = also {
        _queries.add(this)
    }

    protected operator fun GraphQuery.getValue(thisRef: QueryExecutor<T>, property: KProperty<*>) = this

    /* TODO: Is this actually a good idea? */
    protected operator fun String.provideDelegate(thisRef: QueryExecutor<T>, property: KProperty<*>) = also {
        _queries.add(GraphQuery.importQuery(this))
    }

    protected operator fun String.getValue(thisRef: QueryExecutor<T>, property: KProperty<*>) = this

    fun iterateUntilFixedPoint(limit: Int = 50): Result {
        val matchesWithState: List<MatchAndState<T>> = _queries
                .flatMap { it.match(graph) }
                .map { Pair(it, MapProxy(hashMapOf<String, T>()).withDefault { initializer() }) }

        nodeState = MapProxy(hashMapOf<NodeWrapper, T>()).withDefault { initializer() }
        for (i in 0..limit) {
            hasChanged = false
            matchesWithState.asSequence().map(this::executeWithState).forEach(nodeState::putAll)
            if (!hasChanged)
                break

        }

        return Result(nodes = nodeState, groups = matchesWithState.map(MatchAndState<T>::second))
    }

    private fun executeWithState(matchAndState: Pair<GraphQueryMatch, MutableMap<String, T>>): Map<NodeWrapper, T> {
        state = matchAndState.second
        return execute(matchAndState.first)
    }

    private fun execute(match: GraphQueryMatch): Map<NodeWrapper, T> =
            match.entries.asSequence()
                    .filter { it.key.captureGroup().isPresent && functions.containsKey(it.key.captureGroup().get()) }
                    .flatMap { (queryVertex, nodes) ->
                        val ret = functions[queryVertex.captureGroup().get()]!!.invoke(nodes)
                        nodes.map { node -> Pair(node, ret) }
                    }
                    .filter { it.second != null }
                    .filterIsInstance<Pair<NodeWrapper, T>>()
                    .toMap()
}
