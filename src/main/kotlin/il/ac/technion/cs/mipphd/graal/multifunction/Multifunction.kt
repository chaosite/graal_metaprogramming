package il.ac.technion.cs.mipphd.graal.multifunction

import il.ac.technion.cs.mipphd.graal.MethodToGraph
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import il.ac.technion.cs.mipphd.graal.graphquery.GraphQuery
import il.ac.technion.cs.mipphd.graal.graphquery.GraphQueryVertex
//import il.ac.technion.cs.mipphd.graal.utils.CFGWrapper
import il.ac.technion.cs.mipphd.graal.utils.MethodWrapper
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapperUtils.*

fun recursivelyApplyQuery(query: GraphQuery, startingMethod: MethodWrapper, predicate: (MethodWrapper) -> Boolean): Map<MethodWrapper, List<Map<GraphQueryVertex<*>, NodeWrapper>>> {
    val queue = ArrayDeque(listOf(startingMethod))
    val results = mutableMapOf<MethodWrapper, List<Map<GraphQueryVertex<*>, NodeWrapper>>>()

    val methodToGraph = MethodToGraph() // Inject this?

    while (!queue.isEmpty()) {
        val method = queue.removeFirst()
        val cfg = method.toCFG(methodToGraph)
        val queryResults = query.match(cfg)
        results[method] = queryResults
        queryResults.forEach { queryResult ->
            queryResult.values.asSequence()
                .filter(::isInvoke)
                .map(::getTargetMethod)
                .filterNot(results::contains)
                .filter(predicate)
                .forEach(queue::add)
        }
    }

    return results
}