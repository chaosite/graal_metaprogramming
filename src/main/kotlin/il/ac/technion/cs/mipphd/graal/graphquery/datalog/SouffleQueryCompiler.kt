package il.ac.technion.cs.mipphd.graal.graphquery.datalog

import il.ac.technion.cs.mipphd.graal.graphquery.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.isExecutable

class SouffleException(msg: String, cause: Throwable? = null) : Exception(msg, cause)

class CompiledSouffleQuery(
    private val workDir: Path, private val binaryQuery: Path,
    private val inputs: MutableMap<Path, (AnalysisGraph) -> StringBuilder>,
    private val parsers: MutableList<Pair<GraphQuery, (AnalysisGraph) -> QueryResults>>
) : CompiledQuery {
    override fun execute(graph: AnalysisGraph): Map<GraphQuery, QueryResults> {
        for (input in inputs) {
            Files.newBufferedWriter(input.key, Charsets.UTF_8).use {
                it.write(input.value(graph).toString())
            }
        }

        val p = try {
            ProcessBuilder(binaryQuery.toString())
                .directory(workDir.toFile())
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()
        } catch (e: Exception) {
            throw SouffleException("Could not execute Datalog script $binaryQuery with workdir=$workDir", e)
        }

        if (!p.waitFor(5, TimeUnit.MINUTES)) {
            p.destroy()
            throw SouffleException("Datalog script subprocess did not complete within timeout")
        }

        if (p.exitValue() != 0) {
            throw SouffleException(
                "Datalog script subprocess exited with code ${p.exitValue()}, stderr is: \"${
                    p.errorReader().readLine()
                }\""
            )
        }

        println(Files.list(workDir).toList())

        return parsers.associate { it.first to it.second(graph) }
    }
}

class SouffleQueryCompiler(
    private val workDir: Path,
    private val souffleBin: Path = Paths.get("souffle")
) : QueryCompiler {
    companion object {
        private const val NUMBER_TYPE = "number"
        private const val SYMBOL_TYPE = "symbol"

        private const val AUTOINC = "autoinc()"
    }

    override fun compile(queries: List<GraphQuery>): CompiledQuery {
        val state = State(this)

        // Header: Nodes definitions
        state.emitDecl("Node", listOf("id" to NUMBER_TYPE, "label" to SYMBOL_TYPE))
        state.emitInputDecl("Node")
        state.inputs[relationToPath("Node")] = ::serializeGraphNodes

        // Header: Edges definitions
        state.emitDecl(
            "Edge", listOf(
                "src" to NUMBER_TYPE, "dst" to NUMBER_TYPE, "type" to SYMBOL_TYPE, "label" to SYMBOL_TYPE
            )
        )
        state.emitInputDecl("Edge")
        state.inputs[relationToPath("Edge")] = ::serializeGraphEdges

        // Compile queries
        for (query in queries) {
            compileQuery(state, query)
        }

        // Done preparing the Datalog files, execute the compiler
        val binaryPath = executeSouffle(state)

        return CompiledSouffleQuery(workDir, binaryPath, state.inputs, state.outputs)
    }

    private data class State(
        val that: SouffleQueryCompiler,
        val buffer: MutableList<String> = mutableListOf(),
        val inputs: MutableMap<Path, (AnalysisGraph) -> StringBuilder> = mutableMapOf(),
        val outputs: MutableList<Pair<GraphQuery, (AnalysisGraph) -> QueryResults>> = mutableListOf(),
        var numOfQueries: Int = 0
    ) {

        fun emitInputDecl(relation: String) {
            buffer += ".input ${relation}(IO=file, filename=\"${that.relationToPath(relation).fileName}\")"
        }

        fun emitOutputDecl(relation: String) {
            buffer += ".output ${relation}(IO=file, filename=\"${that.relationToPath(relation).fileName}\")"
        }

        fun emitDecl(relation: String, params: List<Pair<String, String>>) {
            buffer += ".decl ${relation}(${params.joinToString(", ") { "${it.first}: ${it.second}" }})"
        }

        fun emitRelation(relation: SouffleRelation) {
            buffer += relation.toString()
        }
    }

    private data class QueryState(
        val id: Int,
        val captureGroups: MutableMap<String, GraphQueryVertex> = mutableMapOf(),
        val vertices: MutableMap<String, GraphQueryVertex> = mutableMapOf()

    )

    private fun executeSouffle(state: State): Path {
        val scriptPath = workDir.resolve("script.dl")
        Files.newBufferedWriter(scriptPath, Charsets.UTF_8).use {
            for (line in state.buffer) it.appendLine(line)
        }

        val compiledPath = workDir.resolve("script").toAbsolutePath()

        val cmdline = arrayOf(
            souffleBin.toString(), "-o", compiledPath.toString(),
            scriptPath.toAbsolutePath().toString()
        )
        val p = try {
            ProcessBuilder(*cmdline)
                .directory(workDir.toFile())
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start()
        } catch (e: Exception) {
            throw SouffleException("Could not execute Souffle compiler, cmdline=$cmdline", e)
        }

        if (!p.waitFor(5, TimeUnit.MINUTES)) {
            p.destroy()
            throw SouffleException("Timed out waiting for Souffle compiler")
        }

        if (p.exitValue() != 0)
            throw SouffleException(
                "Souffle compiler exited with code ${p.exitValue()}, stderr was: \"${
                    p.errorReader().readLine()
                }\""
            )

        if (!compiledPath.isExecutable())
            throw SouffleException("Target ($compiledPath) not executable after running compiler")

        return compiledPath
    }

    private fun compileQuery(state: State, query: GraphQuery) {
        val queryId = state.numOfQueries
        state.numOfQueries += 1
        val queryState = QueryState(queryId)

        // Step 1: Collect all the node conditions and edge conditions, without structure
        compileQueryNodes(state, queryState, query)
        compileQueryEdges(state, queryState, query)

        // Step 2: Construct main query
        compileQueryMain(state, queryState, query)
    }

    private fun relationName(queryState: QueryState, type: String, subType: String) =
        "q${queryState.id}_${type}_${subType}"

    private fun nodeRelationName(queryState: QueryState, nodeName: String) =
        relationName(queryState, "n", nodeName)

    private fun edgeRelationName(queryState: QueryState, src: String, dst: String) =
        relationName(queryState, "e", "${src}_${dst}")

    private fun mainRelationName(queryState: QueryState) =
        "q${queryState.id}_main"

    private fun compileQueryNodes(state: State, queryState: QueryState, query: GraphQuery) {
        /* inline */ fun relName(nodeName: String) = nodeRelationName(queryState, nodeName)
        for (node in query.vertexSet()) {
            state.emitDecl(relName(node.name), listOf("id" to NUMBER_TYPE))
            state.emitInputDecl(relName(node.name))
            val mQuery = node.mQuery as Metadata
            state.inputs[relationToPath(relName(node.name))] = { graph ->
                serializeSingleParameterRelation(
                    graph.vertexSet().asSequence().map(::QueryTargetNode).filter(mQuery::interpret)
                        .map { it.node.index.toInt() }.iterator()
                )
            }
            if (mQuery.options.any { it is MetadataOption.CaptureName }) {
                val captureGroup = mQuery.options.filterIsInstance<MetadataOption.CaptureName>().first().name
                queryState.captureGroups[captureGroup] = node
            } else {
                queryState.vertices[node.name] = node
            }
        }
    }

    private fun compileQueryEdges(state: State, queryState: QueryState, query: GraphQuery) {
        fun relName(edge: GraphQueryEdge) = edgeRelationName(
            queryState, query.getEdgeSource(edge).name,
            query.getEdgeTarget(edge).name
        )
        for (edge in query.edgeSet()) {
            state.emitDecl(relName(edge), listOf("src" to NUMBER_TYPE, "dst" to NUMBER_TYPE))
            state.emitInputDecl(relName(edge))
            val mQuery = edge.mQuery as Metadata
            state.inputs[relationToPath(relName(edge))] = { graph ->
                serializeRelation(graph.edgeSet().asSequence().map { QueryTargetEdge(graph.getEdgeSource(it), it) }
                    .filter(mQuery::interpret)
                    .map { listOf(it.source.index.toInt(), graph.getEdgeTarget(it.edge).index.toInt()) }.iterator())
            }
        }
    }

    private fun compileQueryMain(state: State, queryState: QueryState, query: GraphQuery) {
        state.emitDecl(
            mainRelationName(queryState),
            listOf("?idx" to NUMBER_TYPE) +
                    queryState.captureGroups.map { "?${it.key}" to NUMBER_TYPE } +
                    queryState.vertices.map { "?${it.key}" to NUMBER_TYPE })
        state.emitOutputDecl(mainRelationName(queryState))

        val reverseCaptureGroup = (queryState.captureGroups.map { it.value to it.key }
                + queryState.vertices.map { it.value to it.key }).toMap()
        val vars = hashMapOf<GraphQueryVertex, String>()

        val outputParser = SouffleOutputParser(relationToPath(mainRelationName(queryState)))

        for ((name, vertex) in queryState.captureGroups)
            outputParser.addCaptureGroup(name, vertex)
        for (vertex in queryState.vertices.values)
            outputParser.addVertex(vertex)
        state.outputs.add(query to outputParser::parse)

        state.emitRelation(
            souffle(
                mainRelationName(queryState),
                listOf(AUTOINC) +
                        queryState.captureGroups.keys.map { "?$it" } +
                        queryState.vertices.keys.map { "?$it" }) {
                for (node in query.vertexSet()) {
                    val varName = if (node in reverseCaptureGroup) "?${reverseCaptureGroup[node]}" else node.name
                    vars[node] = varName
                    relation("Node") {
                        param(varName)
                        param("_")
                    }
                    relation(nodeRelationName(queryState, node.name)) { param(varName) }
                }
                for (edge in query.edgeSet()) {
                    val src = query.getEdgeSource(edge)
                    val dst = query.getEdgeTarget(edge)
                    relation("Edge") {
                        param(vars[src]!!)
                        param(vars[dst]!!)
                        param("_")
                        param("_")
                    }
                    relation(edgeRelationName(queryState, src.name, dst.name)) {
                        param(vars[src]!!)
                        param(vars[dst]!!)
                    }
                }
            }
        )
    }

    private fun serializeGraphNodes(graph: AnalysisGraph): StringBuilder =
        serializeRelation(graph.vertexSet().map { listOf(it.index.toInt(), it.nodeName) }.iterator())

    private fun serializeGraphEdges(graph: AnalysisGraph): StringBuilder =
        serializeRelation(
            graph.edgeSet().map {
                listOf(
                    /* src */ graph.getEdgeSource(it).index.toInt(),
                    /* dst */ graph.getEdgeTarget(it).index.toInt(),
                    /* type */ it.baseType(),
                    /* label */ it.label
                )
            }
                .iterator()
        )

    private fun serializeSingleParameterRelation(values: Iterator<Int>): StringBuilder =
        StringBuilder().apply {
            values.forEach { append(it).append("\n") }
        }

    private fun <T> serializeRelation(values: Iterator<List<T>>): StringBuilder =
        StringBuilder().apply {
            values.forEach { row ->
                row.forEachIndexed { i, value ->
                    if (value is String) append('"')
                    append(value)
                    if (value is String) append('"')
                    if (i != row.size - 1) append('\t')
                }
                append('\n')
            }
        }

    private fun relationToPath(relation: String): Path = workDir.resolve("${relation}.csv")

}