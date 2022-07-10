package il.ac.technion.cs.mipphd.graal.graphquery

import arrow.core.Either
import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import org.graalvm.compiler.graph.NodeInterface
import org.jgrapht.Graph
import org.jgrapht.GraphTests

enum class Direction {
    FORWARDS,
    BACKWARDS
}

typealias MatchedNodes = Either<NodeWrapper, List<NodeWrapper>>

fun possibleChildrenMatches(query: GraphQuery, graph: GraalAdapter, queryV: GraphQueryVertex<*>, graphV: NodeWrapper):
        List<Map<GraphQueryVertex<out NodeInterface>, List<MatchedNodes>>> =
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

private fun originOrLast(it: MatchedNodes): NodeWrapper =
    when (it) {
        is Either.Left -> it.a; is Either.Right -> it.b.last()
    }

fun singleStep(
    graph: GraalAdapter,
    queryE: GraphQueryEdge,
    queryW: GraphQueryVertex<*>,
    graphV: NodeWrapper,
    dir: Direction,
): List<MatchedNodes> {


    val r = directionToEdgesOfFunction(graph, dir)(graphV)
        .asSequence()
        .filter { e -> queryE.match(graph.getEdgeSource(e), e) }
        .map() { e ->
            directionToEdgeFunction(graph, dir)(e)
        }
        .map(::listOf)
        .map { Either.Right(it) }
        .filter { queryW.match(originOrLast(it)) }
        .toList()
    return r
}

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
    queryE: GraphQueryEdge,
    queryW: GraphQueryVertex<*>,
    graphStart: NodeWrapper,
    dir: Direction,
): List<MatchedNodes> {
    assert(dir == Direction.FORWARDS) // TODO: Handle backwards Kleene?
    val queue = ArrayDeque<List<NodeWrapper>>()
    val visited = mutableSetOf<NodeWrapper>()
    val ret: MutableList<Either<NodeWrapper, List<NodeWrapper>>> = mutableListOf(Either.Left(graphStart))
    val firstSteps = singleStep(graph, queryE, queryW, graphStart, dir)
    ret.addAll(firstSteps)
    queue.addAll(firstSteps.map { it.orNull()!! })
    var limit = 50;
    while (!queue.isEmpty() && limit-- > 0) {
        val path = queue.removeFirst()
        val graphV = path.last()
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

fun permutations(options: Map<GraphQueryVertex<*>, List<MatchedNodes>>): List<Map<GraphQueryVertex<*>, MatchedNodes>> {
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
    graph: GraalAdapter,
    queryStart: GraphQueryVertex<*>,
): List<Map<GraphQueryVertex<*>, List<NodeWrapper>>> {
    if (!GraphTests.isConnected(query))
        throw RuntimeException("Query is not weakly-connected - this is an error.")
    return graph.vertexSet().filter(queryStart::match).flatMap { bfsMatch(query, graph, queryStart, it) }
}

data class WorkItem(
    val matches: Map<GraphQueryVertex<*>, MatchedNodes>,
    val queue: List<Pair<GraphQueryVertex<*>, MatchedNodes>>,
)

fun bfsMatch(
    query: GraphQuery,
    graph: GraalAdapter,
    queryStart: GraphQueryVertex<*>,
    graphStart: NodeWrapper,
): List<Map<GraphQueryVertex<*>, List<NodeWrapper>>> {
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
    val fullMatches = mutableListOf<Map<GraphQueryVertex<*>, List<NodeWrapper>>>()

    while (!workset.isEmpty()) {
        val (matches, queue) = workset.removeFirst()

        if (queue.isEmpty()) {
            assert(matches.size == query.vertexSet().size) { "matches.size != query.size, matches: $matches" }
            fullMatches.add(matches.mapValues { (_, v) ->
                when (v) {
                    is Either.Left -> listOf()
                    is Either.Right -> v.b
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
                                    (originOrLast(newMatches[q]!!) == m.orNull()?.last()) /* backward match to end of kleene */
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
    matches: List<Map<GraphQueryVertex<*>, List<NodeWrapper>>>
): List<Map<GraphQueryVertex<*>, List<NodeWrapper>>> {
    val repeatedQueryNodes = query.vertexSet().filterIsInstance<GraphQueryVertexM>()
        .filter { (it.mQuery as Metadata).options.contains(MetadataOption.Repeated) }
    return matches
        .groupBy { match -> match.filterKeys { key -> !repeatedQueryNodes.contains(key) } }
        .map { (key, value) -> key.entries + value.flatMap { it.filterKeys(repeatedQueryNodes::contains).entries } }
        .map { match -> match.groupBy { it.key }.mapValues { (_, values) -> values.map { it.value }.flatten() } }
}