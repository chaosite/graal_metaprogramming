package il.ac.technion.cs.mipphd.graal.graphquery.bfs

import arrow.core.Either
import il.ac.technion.cs.mipphd.graal.graphquery.*
import il.ac.technion.cs.mipphd.graal.graphquery.Metadata
import org.jgrapht.Graph
import org.jgrapht.GraphTests

enum class Direction {
    FORWARDS,
    BACKWARDS
}

typealias MatchedNodes = Either<AnalysisNode, List<AnalysisNode>>

fun possibleChildrenMatches(query: GraphQuery, graph: AnalysisGraph, queryV: GraphQueryVertex, graphV: AnalysisNode):
        List<Map<GraphQueryVertex, List<MatchedNodes>>> =
    listOf(query.edgesOf(queryV).mapNotNull { qe ->
        val dir = if (query.getEdgeSource(qe) == queryV) Direction.FORWARDS else Direction.BACKWARDS
        val queryW = directionToEdgeFunction(query, dir)(qe)
        val additionalQueryW = if (dir == Direction.BACKWARDS)
            query.incomingEdgesOf(queryW)
                .filter { it.matchType == GraphQueryEdgeMatchType.KLEENE }
                .map(query::getEdgeSource)
        else listOf()
        if (qe.matchType == GraphQueryEdgeMatchType.KLEENE && dir == Direction.BACKWARDS)
            null // Not handling backwards Kleene edges.
        else
            Pair(
                queryW, (
                        if (qe.matchType == GraphQueryEdgeMatchType.KLEENE)
                            kleeneTransitiveClosure(graph, qe, queryW, graphV, dir)
                        else singleStep(graph, qe, queryW, graphV, dir) + additionalQueryW.flatMap {
                            singleStep(
                                graph,
                                qe,
                                it,
                                graphV,
                                dir
                            )
                        }
                        )
            )
    }.toMap())

private fun originOrLast(it: MatchedNodes): AnalysisNode =
    when (it) {
        is Either.Left -> it.value; is Either.Right -> it.value.last()
    }

fun singleStep(
    graph: AnalysisGraph,
    queryE: GraphQueryEdge,
    queryW: GraphQueryVertex,
    graphV: AnalysisNode,
    dir: Direction,
): List<MatchedNodes> =
    directionToEdgesOfFunction(graph, dir)(graphV)
        .asSequence()
        .filter { e -> queryE.match(graph.getEdgeSource(e), e) }
        .map(directionToEdgeFunction(graph, dir)).map(::listOf).map { Either.Right(it) }
        .filter { queryW.match(originOrLast(it)) }
        .toList()

private fun <V, E> directionToEdgesOfFunction(graph: Graph<V, E>, dir: Direction) = when (dir) {
    Direction.FORWARDS -> graph::outgoingEdgesOf
    Direction.BACKWARDS -> graph::incomingEdgesOf
}

private fun <V, E> directionToEdgeFunction(graph: Graph<V, E>, dir: Direction) = when (dir) {
    Direction.FORWARDS -> graph::getEdgeTarget
    Direction.BACKWARDS -> graph::getEdgeSource
}

fun kleeneTransitiveClosure(
    graph: AnalysisGraph,
    queryE: GraphQueryEdge,
    queryW: GraphQueryVertex,
    graphStart: AnalysisNode,
    dir: Direction,
): List<MatchedNodes> {
    assert(dir == Direction.FORWARDS) // TODO: Handle backwards Kleene?
    val queue = ArrayDeque<List<AnalysisNode>>()
    val visited = mutableSetOf<AnalysisNode>()
    val ret: MutableList<Either<AnalysisNode, List<AnalysisNode>>> = mutableListOf(Either.Left(graphStart))
    val firstSteps = singleStep(graph, queryE, queryW, graphStart, dir)
    ret.addAll(firstSteps)
    queue.addAll(firstSteps.map { it.orNull()!! })

    while (!queue.isEmpty()) {
        val path = queue.removeFirst()
        val graphV = path.last()
        assert(path.indexOf(graphV) == path.size - 1)
        visited.add(graphV)
        graph.outgoingEdgesOf(graphV)
            .asSequence()
            .filter { e -> queryE.match(graphV, e) }
            .map(graph::getEdgeTarget)
            .filter(queryW::match)
            .filterNot(path::contains)
            .map(path::plus)
            .forEach {
                queue.add(it)
                ret.add(Either.Right(it))
            }
    }
    return ret
}

fun permutations(options: Map<GraphQueryVertex, List<MatchedNodes>>): List<Map<GraphQueryVertex, MatchedNodes>> {
    if (options.isEmpty())
        return listOf(mapOf())
    val queryVertices = options.keys.toList()
    val sets = queryVertices.map(options::getValue).map(Iterable<MatchedNodes>::toSet)
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
    graph: AnalysisGraph,
    queryStart: GraphQueryVertex,
): List<Map<GraphQueryVertex, List<AnalysisNode>>> {
    if (!GraphTests.isConnected(query))
        throw RuntimeException("Query is not weakly-connected - this is an error.")

    val metadata = queryStart.mQuery as Metadata
    if (metadata.options.contains(MetadataOption.Repeated))
        throw RuntimeException("Shouldn't start the BFS on a repeated node, pick another")

    return graph.vertexSet().filter(queryStart::match).flatMap { bfsMatch(query, graph, queryStart, it) }
}

data class WorkItem(
    val matches: Map<GraphQueryVertex, MatchedNodes>,
    val queue: List<Pair<GraphQueryVertex, MatchedNodes>>,
)

fun bfsMatch(
    query: GraphQuery,
    graph: AnalysisGraph,
    queryStart: GraphQueryVertex,
    graphStart: AnalysisNode,
): List<Map<GraphQueryVertex, List<AnalysisNode>>> {
    val workset = ArrayDeque<WorkItem>()
    possibleChildrenMatches(query, graph, queryStart, graphStart).map(::permutations).forEach { options ->
        options.forEach {
            workset.add(
                WorkItem(
                    mapOf(Pair(queryStart, Either.Right(listOf(graphStart)))),
                    it.toList()
                )
            )
        }
    }
    val fullMatches = mutableListOf<Map<GraphQueryVertex, List<AnalysisNode>>>()

    while (!workset.isEmpty()) {
        val (matches, queue) = workset.removeFirst()

        if (queue.isEmpty()) {
            assert(matches.size == query.vertexSet().size) { "matches.size != query.size, matches: $matches" }
            fullMatches.add(matches.mapValues { (_, v) ->
                when (v) {
                    is Either.Left -> listOf()
                    is Either.Right -> v.value
                }
            })
            continue
        }
        val (qV, gV) = queue.first()
        val newMatches = matches.plus(queue.first())
        val newQueue = queue.drop(1)
        val childrenMatches = possibleChildrenMatches(query, graph, qV, originOrLast(gV)).map(::permutations)
        childrenMatches.forEach { options ->
            options.forEach { childrenMatch ->
                if (childrenMatch.filter { newMatches.containsKey(it.key) }
                        .all { (q, m) ->
                            (newMatches[q] == m) /* normal case */ ||
                                    (originOrLast(newMatches[q]!!) == m.orNull()
                                        ?.last()) /* backward match to end of kleene */
                        })
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
    matches: List<Map<GraphQueryVertex, List<AnalysisNode>>>
): List<Map<GraphQueryVertex, List<AnalysisNode>>> {
    val repeatedQueryNodes = query.vertexSet()
        .filter { (it.mQuery as Metadata).options.contains(MetadataOption.Repeated) }
    return matches
        .groupBy { match -> match.filterKeys { key -> !repeatedQueryNodes.contains(key) } }
        .map { (key, value) -> key.entries + value.flatMap { it.filterKeys(repeatedQueryNodes::contains).entries } }
        .map { match -> match.groupBy { it.key }.mapValues { (_, values) -> values.map { it.value }.flatten() } }
}