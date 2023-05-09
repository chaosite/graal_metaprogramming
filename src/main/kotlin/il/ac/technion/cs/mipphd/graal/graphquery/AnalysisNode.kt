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
    object Default : AnalysisNode() {
        override fun description() = "default"
        override fun isType(name: String) = false
    }

    /**
     * Node from the Graal IR CFG
     */
    class IR(private val wrappedNode: WrappedIRNodeImpl) : AnalysisNode(), WrappedIRNode by wrappedNode {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true

            other as IR

            return wrappedNode == other.wrappedNode
        }

        override fun hashCode(): Int {
            return wrappedNode.hashCode()
        }

        override val index: UInt
            get() = id.toUInt()

        override val nodeName: String
            get() = "ir${index}"

        override fun description(): String = wrappedNode.shortToString()


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
        val isParameter: Boolean
            get() = wrappedNode.isType("ParameterNode")
    }

    /**
     * Node added to the graph by an analysis
     */
    abstract class Specific : AnalysisNode() {
        companion object {
            protected var indexNameCounter = 0U
        }

        private val indexValue: UInt = indexNameCounter++

        override val index: UInt
            get() = indexValue

        private val className = this.javaClass.simpleName

        override fun isType(name: String) = name == className // TODO: Do something fancier with inheritance?

    }

    open val index: UInt
        get() = hashCode().toUInt()

    /**
     * Name of the node, for use when exporting in GraphViz format.
     */
    open val nodeName: String
        get() = "${this.javaClass.simpleName}$index"

    override fun toString() = "${this.javaClass.simpleName}{${description()}}"

    abstract fun description(): String

    abstract fun isType(name: String): Boolean
}

