package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.utils.GraalIRGraph
import il.ac.technion.cs.mipphd.graal.utils.WrappedIREdge
import il.ac.technion.cs.mipphd.graal.utils.WrappedIRPhiEdge
import org.jgrapht.graph.DirectedPseudograph
import org.jgrapht.nio.DefaultAttribute.createAttribute
import org.jgrapht.nio.dot.DOTExporter
import java.io.StringWriter
import java.io.Writer

open class AnalysisGraph :
    DirectedPseudograph<AnalysisNode, AnalysisEdge>({ AnalysisNode.Default }, { AnalysisEdge.Default }, false) {
    private val exporter: DOTExporter<AnalysisNode, AnalysisEdge> =
        DOTExporter<AnalysisNode, AnalysisEdge> { it.nodeName }.also { exporter ->
            exporter.setVertexAttributeProvider {
                mapOf(
                    "label" to createAttribute(it.toString()),
                    "shape" to createAttribute(
                        when (it) {
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
                        }
                    ),
                    "root" to createAttribute(
                        when (it) {
                            is AnalysisNode.IR -> if (it.isStart) "true" else "false"
                            else -> "false"
                        }
                    )
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
                        when (it) {
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
        export(writer)
        return writer.toString()
    }

    /**
     * Remove all vertices going forward, stopping at branches, and ensuring no orphans remain.
     */
    fun removeAllVerticesForwards(vertices: Collection<AnalysisNode>) {
        val nodeQueue = ArrayDeque(vertices)
        val edgeQueue = ArrayDeque<AnalysisEdge>()

        while (nodeQueue.isNotEmpty()) {
            val node = nodeQueue.removeFirst()

            if (!containsVertex(node))
                continue // we already removed it, hope that's fine

            outgoingEdgesOf(node).forEach {
                val otherNode = getEdgeTarget(it)
                edgeQueue.add(it)
                if (inDegreeOf(otherNode) == 1 || inControlDegreeOf(otherNode) == 1)
                    nodeQueue.add(getEdgeTarget(it))
            }
            incomingEdgesOf(node).forEach {
                val otherNode = getEdgeSource(it)
                edgeQueue.add(it)
                if (degreeOf(otherNode) == 1)
                    nodeQueue.add(otherNode)
            }
            removeAllEdges(edgeQueue)
            edgeQueue.clear()
            removeVertex(node)
        }
    }

    /**
     * Remove all vertices going backwards, stopping at branches, and ensuring no orphans remain.
     */
    fun removeAllVerticesBackwards(vertices: Collection<AnalysisNode>) {
        val nodeQueue = ArrayDeque(vertices)
        val edgeQueue = ArrayDeque<AnalysisEdge>()

        while (nodeQueue.isNotEmpty()) {
            val node = nodeQueue.removeFirst()

            if (!containsVertex(node))
                continue // we already removed it, hope that's fine

            outgoingEdgesOf(node).forEach {
                val otherNode = getEdgeTarget(it)
                edgeQueue.add(it)
                if (degreeOf(otherNode) == 1)
                    nodeQueue.add(otherNode)
            }
            incomingEdgesOf(node).forEach {
                val otherNode = getEdgeSource(it)
                edgeQueue.add(it)
                if (outDegreeOf(otherNode) == 1 || outControlDegreeOf(otherNode) == 1)
                    nodeQueue.add(otherNode)
            }

            removeAllEdges(edgeQueue)
            edgeQueue.clear()
            removeVertex(node)
        }
    }

    fun removeExceptions() {
        // Step 1: find all "exceptionEdge", and remove going forward
        removeAllVerticesForwards(edgeSet().asSequence()
            .filterIsInstance<AnalysisEdge.Control>()
            .filter { it.label == "exceptionEdge" }
            .map(this::getEdgeTarget).toList())


        // Step 2: Find all "Unwind" vertices, and remove going backwards
        removeAllVerticesBackwards(vertexSet().asSequence()
            .filterIsInstance<AnalysisNode.IR>()
            .filter { it.isType("UnwindNode") }
            .toList())
    }

    fun inControlDegreeOf(vertex: AnalysisNode): Int =
        incomingEdgesOf(vertex).asSequence().filterIsInstance<AnalysisEdge.Control>().count()

    fun outControlDegreeOf(vertex: AnalysisNode): Int =
        outgoingEdgesOf(vertex).asSequence().filterIsInstance<AnalysisEdge.Control>().count()

    fun removeLeaves() {
        removeAllVerticesBackwards(vertexSet().asSequence()
            .filterIsInstance<AnalysisNode.IR>()
            .filterNot { it.isReturn }
            .filter { it.isControl }
            .filter { outDegreeOf(it) == 0 }
            .toList())
        assert(vertexSet().find { it is AnalysisNode.IR && it.isReturn } != null)
    }

    fun simplify() {
        removeExceptions()
        removeLeaves()

        assert(vertexSet().find { it is AnalysisNode.IR && it.isStart } != null)
        assert(vertexSet().find { it is AnalysisNode.IR && it.isReturn } != null)
    }
}