package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import org.graalvm.compiler.graph.NodeInterface
import org.jgrapht.Graph
import org.jgrapht.GraphTests

enum class Direction {
    FORWARDS,
    BACKWARDS
}

fun possibleChildrenMatches(query: GraphQuery, graph: GraalAdapter, queryV: GraphQueryVertex<*>, graphV: NodeWrapper):
        List<Map<GraphQueryVertex<out NodeInterface>, List<List<NodeWrapper>>>> =
    listOf(query.edgesOf(queryV).mapNotNull { qe ->
        val dir = if (query.getEdgeSource(qe) == queryV) Direction.FORWARDS else Direction.BACKWARDS
        val queryW = directionToEdgeFunction(query, dir)(qe)
        if (qe.matchType == GraphQueryEdgeMatchType.KLEENE && dir == Direction.BACKWARDS)
            null // Not handling backwards Kleene edges.
        else
            Pair(queryW, (
                    if (qe.matchType == GraphQueryEdgeMatchType.KLEENE)
                        kleeneTransitiveClosure(graph, queryV, qe, graphV, dir)
                            .flatMap { path ->
                                singleStep(
                                    graph,
                                    queryV,
                                    qe,
                                    path.last(),
                                    dir
                                ).map { path + it }
                            } // ??
                    else singleStep(graph, queryV, qe, graphV, dir)
                    ).filter { queryW.match(it.last()) })
    }.toMap())

fun singleStep(
    graph: GraalAdapter,
    queryV: GraphQueryVertex<*>,
    queryE: GraphQueryEdge,
    graphV: NodeWrapper,
    dir: Direction,
): List<List<NodeWrapper>> = directionToEdgesOfFunction(graph, dir)(graphV)
    .filter { e -> queryE.match(graph.getEdgeSource(e), e) }
    .map(directionToEdgeFunction(graph, dir)).map(::listOf)

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
        List<List<NodeWrapper>> {
    assert(dir == Direction.FORWARDS) // TODO: Handle backwards Kleene?
    val queue = ArrayDeque<List<NodeWrapper>>()
    queue.add(listOf(graphStart))
    val visited = mutableSetOf<NodeWrapper>()
    val ret = mutableListOf(listOf(graphStart))

    while (!queue.isEmpty()) {
        val path = queue.removeFirst()
        val graphV = path.last()
        visited.add(graphV)
        val newPaths = graph.outgoingEdgesOf(graphV)
            .asSequence()
            .filter { e -> queryE.match(graphV, e) }
            .map(graph::getEdgeTarget)
            .filter(queryV::match)
            .filterNot(path::contains)
            .map(path::plus)
            .toList()
        newPaths.forEach(queue::add)
        newPaths.forEach(ret::add)
    }
    return ret
}

fun permutations(options: Map<GraphQueryVertex<*>, List<List<NodeWrapper>>>): List<Map<GraphQueryVertex<*>, List<NodeWrapper>>> {
    if (options.isEmpty())
        return listOf(mapOf())
    val queryVertices = options.keys.toList()
    val sets = queryVertices.map(options::getValue).map(Iterable<List<NodeWrapper>>::toSet)
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
): List<Map<GraphQueryVertex<*>, List<NodeWrapper>>> {
    if (!GraphTests.isConnected(query))
        throw RuntimeException("Query is not weakly-connected - this is an error.")
    return graph.vertexSet().filter(queryStart::match).flatMap { bfsMatch(query, graph, queryStart, it) }
}

data class WorkItem(
    val matches: Map<GraphQueryVertex<*>, List<NodeWrapper>>,
    val queue: List<Pair<GraphQueryVertex<*>, List<NodeWrapper>>>,
)

fun bfsMatch(
    query: GraphQuery,
    graph: GraalAdapter,
    queryStart: GraphQueryVertex<*>,
    graphStart: NodeWrapper,
): List<Map<GraphQueryVertex<*>, List<NodeWrapper>>> {
    val workset = ArrayDeque<WorkItem>()
    possibleChildrenMatches(query, graph, queryStart, graphStart).map(::permutations).forEach { options ->
        options.forEach { workset.add(WorkItem(mapOf(Pair(queryStart, listOf(graphStart))), it.toList())) }
    }
    val fullMatches = mutableListOf<Map<GraphQueryVertex<*>, List<NodeWrapper>>>()

    var maxMatches: Map<GraphQueryVertex<*>, List<NodeWrapper>> = mapOf()

    while (!workset.isEmpty()) {
        val (matches, queue) = workset.removeFirst()

        if (matches.size > maxMatches.size)
            maxMatches = matches

        if (queue.isEmpty()) {
            assert(matches.size == query.vertexSet().size)
            fullMatches.add(matches)
            continue
        } else {
            //assert(matches.size < query.vertexSet().size)
        }
        val (qV, gV) = queue.first()
        val newMatches = matches.plus(queue.first())
        val newQueue = queue.drop(1)
        val childrenMatches = possibleChildrenMatches(query, graph, qV, gV.last()).map(::permutations)
        childrenMatches.forEach { options ->
            options.forEach { childrenMatch ->
                // TODO: Why is last() needed?
                if (childrenMatch.filter { newMatches.containsKey(it.key) }
                        .all { newMatches.getValue(it.key).last() == it.value.last() })
                    workset.add(
                        WorkItem(
                            newMatches,
                            newQueue.plus(childrenMatch.filterNot { newMatches.containsKey(it.key) }.toList())
                        )
                    )
            }
        }
    }

    return groupRepeated(query, fullMatches)
}

fun groupRepeated(
    query: GraphQuery,
    matches: List<Map<GraphQueryVertex<*>, List<NodeWrapper>>>
): List<Map<GraphQueryVertex<*>, List<NodeWrapper>>> {
    val repeatedQueryNodes = query.vertexSet().filterIsInstance<GraphQueryVertexM>()
        .filter { (it.mQuery as Metadata).options.contains(MetadataOption.Repeated) }
    return matches
        .groupBy { match -> match.filterKeys { key -> !repeatedQueryNodes.contains(key) } }
        .map { (key, value) -> key.entries + value.flatMap { it.filterKeys(repeatedQueryNodes::contains).entries } }
        .map { match -> match.groupBy { it.key }.mapValues { (_, values) -> values.map { it.value }.flatten() } }
}