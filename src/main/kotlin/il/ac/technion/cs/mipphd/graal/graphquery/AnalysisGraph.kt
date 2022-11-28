package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.utils.GraalIRGraph
import il.ac.technion.cs.mipphd.graal.utils.WrappedIREdge
import il.ac.technion.cs.mipphd.graal.utils.WrappedIRPhiEdge
import org.jgrapht.graph.DirectedPseudograph
import org.jgrapht.nio.DefaultAttribute.createAttribute
import org.jgrapht.nio.dot.DOTExporter
import java.io.StringWriter
import java.io.Writer

class AnalysisGraph :
    DirectedPseudograph<AnalysisNode, AnalysisEdge>({ AnalysisNode.Default }, { AnalysisEdge.Default }, false) {
    private val exporter: DOTExporter<AnalysisNode, AnalysisEdge> = DOTExporter { it.index.toString() }

    init {
        exporter.setVertexAttributeProvider {
            mapOf(
                "label" to createAttribute(it.toString()),
                "shape" to createAttribute(when(it) {
                    is AnalysisNode.IR -> when {
                        it.isMerge -> "diamond"
                        it.isStart -> "house"
                        it.isReturn -> "invhouse"
                        it.isControl -> "box"
                        it.isData -> "ellipse"
                        else -> ""
                    }
                    is AnalysisNode.Specific -> "note"
                    is AnalysisNode.Default -> "star"
                })
            )
        }
        exporter.setEdgeAttributeProvider {
            mapOf(
                "label" to createAttribute(it.label),
                "color" to createAttribute(
                    when (it) {
                        is AnalysisEdge.Data -> "blue"
                        is AnalysisEdge.Control -> "red"
                        is AnalysisEdge.Association -> "black"
                        is AnalysisEdge.Extra -> "purple"
                        is AnalysisEdge.Default -> "yellow"
                    }
                ),
                "style" to createAttribute(
                    when (it) {
                        is AnalysisEdge.Association -> "dashed"
                        else -> ""
                    }
                ),
                "weight" to createAttribute(
                    when(it) {
                        is AnalysisEdge.Control -> "10"
                        else -> "1"
                    }
                )
            )
        }
    }

    companion object {
        fun fromIR(graal: GraalIRGraph): AnalysisGraph {
            val g = AnalysisGraph()
            for (node in graal.vertexSet()) {
                g.addVertex(AnalysisNode.IR(node))
            }
            for (irEdge in graal.edgeSet()) {
                val edge = when (irEdge.kind) {
                    WrappedIREdge.DATA -> if (irEdge is WrappedIRPhiEdge)
                        AnalysisEdge.Phi(
                            irEdge.label,
                            g.vertexSet().filterIsInstance<AnalysisNode.IR>()
                                .find { it.node() == irEdge.from.node() }!!
                        )
                    else
                        AnalysisEdge.PureData(irEdge.label)

                    WrappedIREdge.CONTROL -> AnalysisEdge.Control(irEdge.label)
                    WrappedIREdge.ASSOCIATED -> AnalysisEdge.Association(irEdge.label)
                    else -> throw IllegalArgumentException("Unexpected $irEdge of type ${irEdge.javaClass}")
                }
                g.addEdge(
                    AnalysisNode.IR(graal.getEdgeSource(irEdge)),
                    AnalysisNode.IR(graal.getEdgeTarget(irEdge)),
                    edge
                )
            }
            return g
        }
    }

    fun export(output: Writer) {
        exporter.exportGraph(this, output)
    }

    fun export(): String {
        val writer = StringWriter()
        exporter.exportGraph(this, writer)
        return writer.toString()
    }
}