package il.ac.technion.cs.mipphd.graal.utils

import il.ac.technion.cs.mipphd.graal.graphquery.AnalysisEdge
import il.ac.technion.cs.mipphd.graal.graphquery.AnalysisNode
import org.jgrapht.Graph
import org.jgrapht.nio.BaseExporter
import org.jgrapht.nio.GraphExporter
import org.jgrapht.nio.IntegerIdProvider
import java.io.PrintWriter
import java.io.Writer
import java.util.Collections

class SouffleExporter : BaseExporter<AnalysisNode, AnalysisEdge>(IntegerIdProvider()),
    GraphExporter<AnalysisNode, AnalysisEdge> {
    private val validatedIds = HashMap<AnalysisNode, String>()


    override fun exportGraph(g: Graph<AnalysisNode, AnalysisEdge>, writer: Writer) {
        val out = PrintWriter(writer)

        // Graph attributes
        for ((key, value) in graphAttributeProvider.orElse(Collections::emptyMap).get().entries) {

        }

        // Vertices
        for (v in g.vertexSet()) {
            val name = when(v) {
                is AnalysisNode.IR -> v.type
                else -> v.description()
            }
            out.print("Node")
            out.println(listOf(idOf(v), "\"${name}\"").joinToString(", ", "(", ")."))

            // TODO: Add attributes as extra relations.
        }

        out.println()

        // Edges
        for (e in g.edgeSet()) {
            val src = idOf(g.getEdgeSource(e))
            val dst = idOf(g.getEdgeTarget(e))
            val type = when (e) {
                is AnalysisEdge.Data -> "data"
                is AnalysisEdge.Control -> "control"
                is AnalysisEdge.Association -> "association"
                is AnalysisEdge.Extra -> "extra"
                is AnalysisEdge.Default -> throw RuntimeException("unexpected default edge")
            }
            out.print("Edge")
            out.println(listOf(src, dst, "\"$type\"", "\"${e.label}\"").joinToString(", ", "(", ")."))

            // TODO: Add attributes as extra relations.
        }

        out.flush()
    }

    private fun idOf(n: AnalysisNode) = validatedIds.computeIfAbsent(n, this::getVertexId)
}