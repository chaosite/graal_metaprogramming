package il.ac.technion.cs.mipphd.graal.graphquery

import org.jgrapht.graph.DefaultEdge

/**
 * Edge in analysis graph.
 *
 * @param label Edge label.
 */
sealed class AnalysisEdge private constructor(val label: String) : DefaultEdge() {
    /**
     * Initial empty edge, default case that should not appear in an actual analysis graph.
     */
    object Default : AnalysisEdge("")

    /**
     * Data edge
     */
    sealed class Data(label: String) : AnalysisEdge(label)

    /**
     * Data edge (non-phi case)
     */
    class PureData(label: String) : Data(label)

    /**
     * Data edge (phi case)
     */
    class Phi(label: String, val from : AnalysisNode.IR) : Data(label)

    /**
     * Control edge
     */
    class Control(label: String) : AnalysisEdge(label)

    /**
     * Association edge
     */
    class Association(label: String) : AnalysisEdge(label)

    /**
     * Extra edge (represents analysis specific association)
     */
    abstract class Extra(label: String) : AnalysisEdge(label)

    /**
     * Equals for edges is "==="
     */
    override fun equals(other: Any?): Boolean {
        return this === other
    }

    // This is redundant, but making it explicit
    override fun hashCode() = super.hashCode()
}
