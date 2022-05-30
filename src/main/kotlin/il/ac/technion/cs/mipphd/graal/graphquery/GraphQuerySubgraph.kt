package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.utils.EdgeWrapper
import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import org.graalvm.compiler.graph.NodeInterface
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.DirectedPseudograph

fun subgraph(origquery: GraphQuery, graph: GraalAdapter, origMatches: Map<GraphQueryVertex<out NodeInterface>, List<NodeWrapper>>): AsSubgraph<NodeWrapper, EdgeWrapper> {
    throw Exception("Use Pseudo version")

    val (query, matches) = removeEmptyEdges(origquery, origMatches) // handle empty kleene clause - can now assume that a query neighbour is a graal neighbour
    val subgraphVertices = HashSet<NodeWrapper>()
    val subgraphEdges = HashSet<EdgeWrapper>()
    val queryNodes = ArrayDeque(matches.keys)
    while(!queryNodes.isEmpty()){
        val n = queryNodes.removeFirst()
        val queryMatches = matches[n]!!
        subgraphVertices.addAll(queryMatches)
        for ((g1,g2) in queryMatches.zipWithNext()){
            var graalEdges =graph.outgoingEdgesOf(g1)
                .filter { e -> graph.getEdgeTarget(e) == g2 }.first()
            // todo same as below - handle multiple edges between g1 and g2 and choose by match

            subgraphEdges.add(graalEdges)
        }
        val graalNode = queryMatches.last() //can do safely because removeEmptyEdges
        for(qE in query.outgoingEdgesOf(n)){
            val nextQueryNode = query.getEdgeTarget(qE)
            val nextGraalNode = matches[nextQueryNode]!!.first() // can do safely because removeEmptyEdges

            //choose correct graal edge
            var graalEdges =graph.outgoingEdgesOf(graalNode)
                .filter { e -> graph.getEdgeTarget(e) == nextGraalNode }

            //choose only matched edges
            if(graalEdges.size > 1){
                graalEdges = graalEdges.filter { e -> qE.match(nextGraalNode,e) }
            }

            //val chosenEdge = graalEdges.first()
            subgraphEdges.addAll(graalEdges)
        }
    }
     return AsSubgraph<NodeWrapper, EdgeWrapper>(graph, subgraphVertices, subgraphEdges);
}

fun removeEmptyEdges(queryGraph: GraphQuery, matches: Map<GraphQueryVertex< out NodeInterface>, List<NodeWrapper>>) :
        Pair<GraphQuery, Map<GraphQueryVertex<out NodeInterface>, List<NodeWrapper>>> {

    // todo - not sure is this works with multiple kleene edges

    val emptyNodes = matches.filter { it.value.isEmpty() }.map { it.key }
    if(emptyNodes.isEmpty()) return Pair(queryGraph, matches)
    val query : GraphQuery = (queryGraph.clone() as GraphQuery)
    for (emptyNode in emptyNodes){
        val outgoings = queryGraph.outgoingEdgesOf(emptyNode)
        val sources = queryGraph.incomingEdgesOf(emptyNode).map { query.getEdgeSource(it) }
        query.removeVertex(emptyNode)
        for (o in outgoings){
            for (s in sources){
                query.addEdge(s,queryGraph.getEdgeTarget(o), GraphQueryEdge(o.mQuery))
            }
        }
    }

    return Pair(query, matches.filter { !emptyNodes.contains(it.key) })
}
