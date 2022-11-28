package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.utils.WrappedIRNode
import il.ac.technion.cs.mipphd.graal.utils.WrappedIRNodeImpl

/**
 * Node in the analysis graph.
 */
sealed class AnalysisNode private constructor() {

    /**
     * Default node, should not be present in an actual analysis graph.
     */
    object Default : AnalysisNode()

    /**
     * Node from the Graal IR CFG
     */
    class IR(private val wrappedNode: WrappedIRNodeImpl) : AnalysisNode(), WrappedIRNode by wrappedNode {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true

            other as IR

            if (wrappedNode != other.wrappedNode) return false

            return true
        }

        override fun hashCode(): Int {
            return wrappedNode.hashCode()
        }

        override val index: Int
            get() = id.toInt()

        override fun toString() = "${this.javaClass.simpleName}(${wrappedNode.shortToString()})"

        val isMerge: Boolean
            get() = wrappedNode.isType("MergeNode")
        val isStart: Boolean
            get() = wrappedNode.isType("StartNode")
        val isControl: Boolean
            get() = wrappedNode.isType("FixedNode")
        val isData: Boolean
            get() = !isControl
        val isReturn: Boolean
            get() = wrappedNode.isType("ReturnNode")
    }

    /**
     * Node added to the graph by an analysis
     */
    abstract class Specific : AnalysisNode()

    open val index: Int
        get() = hashCode()

    override fun toString() = "${this.javaClass.simpleName}{..}"
}

