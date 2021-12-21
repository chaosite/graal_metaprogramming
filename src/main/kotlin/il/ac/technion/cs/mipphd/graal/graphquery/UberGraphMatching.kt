package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import org.graalvm.compiler.graph.NodeInterface
import kotlin.streams.toList

typealias MatchedQueryPorts = List<MutableMap<GraphQueryVertex<out NodeInterface>, MutableList<NodeWrapper>>>
fun uberMatch(
    cfg: GraalAdapter,
    queries: List<GraphQuery>,
    connectedPorts: List<Pair<GraphQueryVertex<out NodeInterface>?, GraphQueryVertex<out NodeInterface>?>>
): MutableList<MutableMap<GraphQueryVertex<out NodeInterface>, MutableList<NodeWrapper>>> {
    val results = mutableListOf<MutableMap<GraphQueryVertex<out NodeInterface>, MutableList<NodeWrapper>>>();
    if(queries.size <= 1){
        println("Run query.matchPorts")
        return results;
    }
    val queriesPortsResults: List<MatchedQueryPorts> = queries.parallelStream().map{ query ->
        query.matchPorts(cfg).values.toList()
    }.toList()

    val product = cartesianProduct(queriesPortsResults[0], queriesPortsResults[1], *queriesPortsResults.drop(2).toTypedArray())
    product.forEach { ps ->
        val valid = arePortsMatching(ps,connectedPorts);
        if(valid){
            val m = mutableMapOf<GraphQueryVertex<out NodeInterface>, MutableList<NodeWrapper>>();
            ps.forEach{
                m.putAll(it)
            }
            results.add(m)
        }
    }
    return results;

}
fun arePortsMatching(ps : MatchedQueryPorts,
    connectedPorts: List<Pair<GraphQueryVertex<out NodeInterface>?, GraphQueryVertex<out NodeInterface>?>>

): Boolean {
    connectedPorts.forEach { (v1,v2) ->
        val v1Queries = ps.filter { p -> p.containsKey(v1) }
        val v2Queries = ps.filter { p -> p.containsKey(v2) }
        v1Queries.forEach(){ v1q ->
            val v1Node = v1q[v1]?.get(0)
            v2Queries.forEach() { v2q ->
                val v2Node = v2q[v2]?.get(0)
                if(v1Node != v2Node){
                    return false;
                }
            }
        }
    }
    return true;
}
fun cartesianProduct(a: MatchedQueryPorts, b: MatchedQueryPorts, vararg sets: MatchedQueryPorts): Set<MatchedQueryPorts> =
    (setOf(a, b).plus(sets))
        .fold(listOf(listOf<MutableMap<GraphQueryVertex<out NodeInterface>, MutableList<NodeWrapper>>>())) { acc, set ->
            acc.flatMap { list -> set.map { element -> list + element } }
        }
        .toSet()