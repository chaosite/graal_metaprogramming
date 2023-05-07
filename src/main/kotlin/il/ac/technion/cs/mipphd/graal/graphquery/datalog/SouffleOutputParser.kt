package il.ac.technion.cs.mipphd.graal.graphquery.datalog

import il.ac.technion.cs.mipphd.graal.graphquery.AnalysisGraph
import il.ac.technion.cs.mipphd.graal.graphquery.AnalysisNode
import il.ac.technion.cs.mipphd.graal.graphquery.GraphQueryVertex
import il.ac.technion.cs.mipphd.graal.graphquery.QueryResults
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.bufferedReader


class SouffleOutputParser(val filename: Path) {
    fun addCaptureGroup(name: String, vertex: GraphQueryVertex): SouffleOutputParser {
        spec.add(CaptureSpec(vertex, name))
        return this
    }

    fun addVertex(vertex: GraphQueryVertex): SouffleOutputParser {
        spec.add(VertexSpec(vertex))
        return this
    }

    fun addSubparser(subparser: SouffleOutputParser) : SouffleOutputParser {
        spec.add(KeySpec(subparser))
        return this
    }

    fun captureGroups(): List<String> = spec.flatMap {
        when (it) {
            is IdSpec -> listOf()
            is VertexSpec -> listOf()
            is CaptureSpec -> listOf(it.name)
            is KeySpec -> it.parser.captureGroups()
        }
    }

    fun queryVertices(): List<GraphQueryVertex> = spec.flatMap {
        when (it) {
            is IdSpec -> listOf()
            is VertexSpec -> listOf()
            is CaptureSpec -> listOf(it.queryVertex)
            is KeySpec -> it.parser.queryVertices()
        }
    }

    fun parse(graph: AnalysisGraph): QueryResults = filename.bufferedReader(Charsets.UTF_8).use { reader ->
        val lines = reader.lines().map { l -> l.split('\t').map(String::toUInt) }
        val idIndex = spec.indexOfFirst { it is IdSpec } // Assumes primary key is unique
        lines
            .map { row ->
                row[idIndex] to row.mapIndexed { idx, value ->
                    when (val s = spec[idx]) {
                        is IdSpec -> null
                        is KeySpec -> TODO()
                        is VertexSpec -> s.queryVertex to graph.findNode(value)!!
                        is CaptureSpec -> s.queryVertex to graph.findNode(value)!!
                    }
                }.filterNotNull()
            }
            .collect(Collectors.groupingBy { it.first })
            .values
            .asSequence()
            .map { it.flatMap(Pair<UInt, List<Pair<GraphQueryVertex, AnalysisNode>>>::second) }
            .map { it.stream().collect(Collectors.groupingBy(Pair<GraphQueryVertex, AnalysisNode>::first))}
            .map { it.mapValues { k -> k.value.map(Pair<GraphQueryVertex, AnalysisNode>::second) } }
            .toList()
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


        private data class KeySpec(
            val parser: SouffleOutputParser
        ) : SpecItem()
    }

    private val spec: MutableList<SpecItem> = mutableListOf(IdSpec)
}