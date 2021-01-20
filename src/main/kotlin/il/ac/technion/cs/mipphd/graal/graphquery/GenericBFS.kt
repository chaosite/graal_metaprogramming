package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.GraalAdapter
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import org.graalvm.compiler.graph.NodeInterface
import org.jgrapht.Graph
import org.jgrapht.GraphTests

enum class Direction {
    FORWARDS,
    BACKWARDS
}

fun possibleChildrenMatches(query: GraphQuery, graph: GraalAdapter, queryV: GraphQueryVertex<*>, graphV: NodeWrapper):
        List<Map<GraphQueryVertex<out NodeInterface>, List<NodeWrapper>>> = listOf(query.edgesOf(queryV).map { qe ->
    val dir = if (query.getEdgeSource(qe) == queryV) Direction.FORWARDS else Direction.BACKWARDS
    val queryW = directionToEdgeFunction(query, dir)(qe)
    if (qe.matchType == GraphQueryEdgeMatchType.KLEENE && dir == Direction.BACKWARDS)
        null // Not handling backwards Kleene edges.
    else
    Pair(queryW, (
            if (qe.matchType == GraphQueryEdgeMatchType.KLEENE)
                kleeneTransitiveClosure(graph, queryV, qe, graphV, dir)
                    .flatMap { singleStep(graph, queryV, qe, it, dir) } // ??
            else singleStep(graph, queryV, qe, graphV, dir)
            ).filter(queryW::match))
}.filterNotNull().toMap())

fun singleStep(
    graph: GraalAdapter,
    queryV: GraphQueryVertex<*>,
    queryE: GraphQueryEdge,
    graphV: NodeWrapper,
    dir: Direction,
): List<NodeWrapper> = directionToEdgesOfFunction(graph, dir)(graphV)
    .filter { e -> queryE.type.match(e.label) }
    .map(directionToEdgeFunction(graph, dir))

private fun <V, E> directionToEdgesOfFunction(graph: Graph<V, E>, dir: Direction) = when (dir) {
    Direction.FORWARDS -> graph::outgoingEdgesOf
    Direction.BACKWARDS -> graph::incomingEdgesOf
}

private fun <V, E> directionToEdgeFunction(graph: Graph<V, E>, dir: Direction) = when (dir) {
    Direction.FORWARDS -> graph::getEdgeTarget
    Direction.BACKWARDS -> graph::getEdgeSource
}

fun kleeneTransitiveClosure(
    graph: GraalAdapter,
    queryV: GraphQueryVertex<*>,
    queryE: GraphQueryEdge,
    graphStart: NodeWrapper,
    dir: Direction,
):
        List<NodeWrapper> {
    assert(dir == Direction.FORWARDS) // TODO: Handle backwards Kleene?
    val queue = ArrayDeque<NodeWrapper>()
    queue.add(graphStart)
    val visited = mutableSetOf<NodeWrapper>()

    while (!queue.isEmpty()) {
        val graphV = queue.removeFirst()
        visited.add(graphV)
        graph.outgoingEdgesOf(graphV)
            .filter { e -> queryE.type.match(e.label) }
            .map(graph::getEdgeTarget)
            .filter(queryV::match)
            .filterNot(visited::contains)
            .forEach(queue::add)
    }
    return visited.toList()
}

fun permutations(options: Map<GraphQueryVertex<*>, List<NodeWrapper>>): List<Map<GraphQueryVertex<*>, NodeWrapper>> {
    if (options.isEmpty())
        return listOf(mapOf())
    val queryVertices = options.keys.toList()
    val sets = queryVertices.map(options::getValue).map(Iterable<NodeWrapper>::toSet)
    if (queryVertices.size <= 1) {
        return sets[0].map { mapOf(Pair(queryVertices[0], it)) }
    }
    val permutations = cartesianProduct(*sets.toTypedArray())
    // TODO: Filter repeats?
    return permutations.map { queryVertices.zip(it).toMap() }
}

fun <T> cartesianProduct(vararg sets: Set<T>): Set<List<T>> =
    sets
        .fold(listOf(listOf<T>())) { acc, set ->
            acc.flatMap { list -> set.map { element -> list + element } }
        }
        .toSet()

fun bfsMatch(
    query: GraphQuery,
    graph: GraalAdapter,
    queryStart: GraphQueryVertex<*>,
): List<Map<GraphQueryVertex<*>, NodeWrapper>> {
    if (!GraphTests.isConnected(query))
        throw RuntimeException("Query is not weakly-connected - this is an error.")
    return graph.vertexSet().filter(queryStart::match).flatMap { bfsMatch(query, graph, queryStart, it) }
}

data class WorkItem(
    val matches: Map<GraphQueryVertex<*>, NodeWrapper>,
    val queue: List<Pair<GraphQueryVertex<*>, NodeWrapper>>,
)

fun bfsMatch(
    query: GraphQuery,
    graph: GraalAdapter,
    queryStart: GraphQueryVertex<*>,
    graphStart: NodeWrapper,
): List<Map<GraphQueryVertex<*>, NodeWrapper>> {
    val workset = ArrayDeque<WorkItem>()
    possibleChildrenMatches(query, graph, queryStart, graphStart).map(::permutations).forEach { options ->
        options.forEach { workset.add(WorkItem(mapOf(Pair(queryStart, graphStart)), it.toList())) }
    }
    val fullMatches = mutableListOf<Map<GraphQueryVertex<*>, NodeWrapper>>()

    while (!workset.isEmpty()) {
        val (matches, queue) = workset.removeFirst()
        if (queue.isEmpty()) {
            assert(matches.size == query.vertexSet().size)
            fullMatches.add(matches)
            continue
        } else {
            assert(matches.size < query.vertexSet().size)
        }
        val (qV, gV) = queue.first()
        val newMatches = matches.plus(queue.first())
        val newQueue = queue.drop(1)
        val childrenMatches = possibleChildrenMatches(query, graph, qV, gV).map(::permutations)
        childrenMatches.forEach { options ->
            options.forEach { childrenMatch ->
                if (childrenMatch.filter { newMatches.containsKey(it.key) }.all { newMatches[it.key] == it.value })
                    workset.add(WorkItem(newMatches,
                        newQueue.plus(childrenMatch.filterNot { newMatches.containsKey(it.key) }.toList())))
            }
        }
    }

    return fullMatches
}