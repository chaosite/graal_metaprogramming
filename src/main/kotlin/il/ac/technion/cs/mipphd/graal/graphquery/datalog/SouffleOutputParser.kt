package il.ac.technion.cs.mipphd.graal.graphquery.datalog

import il.ac.technion.cs.mipphd.graal.graphquery.AnalysisGraph
import il.ac.technion.cs.mipphd.graal.graphquery.AnalysisNode
import il.ac.technion.cs.mipphd.graal.graphquery.GraphQueryVertex
import il.ac.technion.cs.mipphd.graal.graphquery.QueryResults
import java.nio.file.Path
import kotlin.io.path.bufferedReader


class SouffleOutputParser(private val filename: Path) {
    fun addCaptureGroup(name: String, vertex: GraphQueryVertex): SouffleOutputParser =
        apply { spec.add(CaptureSpec(vertex, name)) }

    fun addVertex(vertex: GraphQueryVertex): SouffleOutputParser = apply { spec.add(VertexSpec(vertex)) }

    fun addForeignReference(): SouffleOutputParser = apply { spec.add(KeySpec) }

    fun addSubparser(subparser: SouffleOutputParser): SouffleOutputParser = apply { subparsers.add(subparser) }

    fun parse(graph: AnalysisGraph) : QueryResults =
        parseRaw(graph)
            .groupBy { it.first.first }
            .values
            .asSequence()
            .map { it.flatMap(Pair<Key, List<Pair<GraphQueryVertex, AnalysisNode>>>::second) }
            .map { it.groupBy(Pair<GraphQueryVertex, AnalysisNode>::first) }
            .map { it.mapValues { it.value.map(Pair<GraphQueryVertex, AnalysisNode>::second) } }
            .toList()

    private fun parseRaw(graph: AnalysisGraph): List<Pair<Key, List<Pair<GraphQueryVertex, AnalysisNode>>>> {
        val ret = mutableListOf<Pair<Key, List<Pair<GraphQueryVertex, AnalysisNode>>>>()
        val res = readFile(graph)
        if (this.subparsers.isEmpty())
            return res
        val subres = parseSubparsers(graph)
        // TODO: HORRIBLY INEFFICIENT, REDO DURING DAYTIME
        for ((foreignKey, subline) in subres) {
            for ((key, line) in res) {
                if ((key.first) in foreignKey) {
                    ret.add(key to line + subline)
                }
            }
        }
        return ret
    }

    private fun parseSubparsers(graph: AnalysisGraph) = subparsers
        .asSequence()
        .flatMap { it.parseRaw(graph) }
        .map { it.first.second.toSet() to it.second }
        .toList()

    private fun readFile(graph: AnalysisGraph): List<Pair<Key, List<Pair<GraphQueryVertex, AnalysisNode>>>> =
        filename.bufferedReader(Charsets.UTF_8).use { reader ->
            val lines = reader.lines().map { l -> l.split('\t').map(String::toUInt) }
            val idIndex = spec.indexOfFirst { it is IdSpec } // Assumes primary key exists and is unique
            val keyIndices = spec.filterIsInstance<KeySpec>().map(spec::indexOf)
            lines
                .map { row ->
                    (row[idIndex] to keyIndices.map(row::get)) to row.mapIndexed { idx, value ->
                        when (val s = spec[idx]) {
                            is IdSpec -> null
                            is KeySpec -> null
                            is VertexSpec -> s.queryVertex to graph.findNode(value)!!
                            is CaptureSpec -> s.queryVertex to graph.findNode(value)!!
                        }
                    }.filterNotNull()
                }.toList() // Read list to memory so we can close the file
        }

    companion object {

        private sealed class SpecItem

        private object IdSpec : SpecItem()

        private data class VertexSpec(
            val queryVertex: GraphQueryVertex,
        ) : SpecItem()

        private data class CaptureSpec(
            val queryVertex: GraphQueryVertex,
            val name: String
        ) : SpecItem()


        private object KeySpec : SpecItem()
    }

    private val spec: MutableList<SpecItem> = mutableListOf(IdSpec)
    private val subparsers: MutableList<SouffleOutputParser> = mutableListOf()
}

private typealias Key = Pair<UInt, List<UInt>>