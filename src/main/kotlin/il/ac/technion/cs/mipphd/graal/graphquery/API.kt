package il.ac.technion.cs.mipphd.graal.graphquery

import com.beust.klaxon.json
import il.ac.technion.cs.mipphd.graal.utils.GraalIRGraph
import il.ac.technion.cs.mipphd.graal.utils.WrappedIRNodeImpl
import java.io.StringWriter

fun compileAndQuery(g: GraalIRGraph, q: String): String {

    val sw = StringWriter()
    g.exportQuery(sw)
    val mGraph = sw.buffer.toString()

    val qParsed = GraphQuery.importQuery(q)

    val matches = qParsed.match(g)
    val results = matches
        .stream()
        .map {
            it.entries
                .map { (qV, gVs) -> Pair(qV.name, gVs.map(AnalysisNode::toString)) }
        }
    return json { obj(
        "graph" to mGraph,
        "queryResults" to array(results.map { obj(it.map { (qV, gVs) -> qV to array(gVs) }) }.toList()),
        "queryVertices" to obj(qParsed.vertexSet().map { v -> v.name to v.captureGroup().orElse("") }))
    }.toJsonString()
}