package il.ac.technion.cs.mipphd.graal.graphquery.datalog

import il.ac.technion.cs.mipphd.graal.graphquery.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.NavigableMap
import java.util.TreeMap
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
        private const val WILD = "_"

        private const val IDX_PARAM = "?idx"
        private const val REF_PARAM = "?ref"

        private const val NODE_RELATION = "Node"
        private const val EDGE_RELATION = "Edge"
    }

    override fun compile(queries: List<GraphQuery>): CompiledQuery {
        val state = State(this)

        // Header: Nodes definitions
        state.emitDecl(NODE_RELATION, listOf("id" to NUMBER_TYPE, "label" to SYMBOL_TYPE))
        state.emitInputDecl(NODE_RELATION)
        state.inputs[relationToPath(NODE_RELATION)] = ::serializeGraphNodes

        // Header: Edges definitions
        state.emitDecl(
            EDGE_RELATION,
            listOf("src" to NUMBER_TYPE, "dst" to NUMBER_TYPE, "type" to SYMBOL_TYPE, "label" to SYMBOL_TYPE)
        )
        state.emitInputDecl(EDGE_RELATION)
        state.inputs[relationToPath(EDGE_RELATION)] = ::serializeGraphEdges

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
        val reverseCaptureGroups: MutableMap<GraphQueryVertex, String> = mutableMapOf(),
        val vertices: MutableMap<String, GraphQueryVertex> = mutableMapOf(),
        val repeatedVertices: MutableMap<String, GraphQueryVertex> = mutableMapOf(),
        val orderedKleeneVertices: MutableList<GraphQueryVertex> = mutableListOf(),
        val kleeneVertices: MutableMap<String, GraphQueryVertex> = mutableMapOf(),
        val repeatedParsers: MutableList<SouffleOutputParser> = mutableListOf()
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

        // Step 2: Construct relations for repeated vertices
        compileQueryRepeated(state, queryState, query)

        // Step 3: Construct relation for main query
        compileQueryMain(state, queryState, query)
    }

    private fun relationName(queryState: QueryState, type: String, subType: String) =
        "q${queryState.id}_${type}_${subType}"

    private fun nodeRelationName(queryState: QueryState, nodeName: String) =
        relationName(queryState, "n", nodeName)

    private fun edgeRelationName(queryState: QueryState, src: String, dst: String) =
        relationName(queryState, "e", "${src}_${dst}")

    private fun repeatedRelationName(queryState: QueryState, nodeName: String) =
        relationName(queryState, "r", nodeName)

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
            if (query.isSunVertex(node)) {
                queryState.repeatedVertices[node.name] = node
                // TODO: Add checks for Sun vertices
            }
            if (query.isKleeneVertex(node)) {
                queryState.kleeneVertices[node.name] = node
                // TODO: Add checks for Kleene vertices
            }
        }

        queryState.orderedKleeneVertices.addAll(
            queryState.repeatedVertices.values.sortedWith(
                compareBy({ -query.edgesOf(it).size }, { it })
            )
        )

        queryState.reverseCaptureGroups.putAll((queryState.captureGroups.map { it.value to it.key }
                + queryState.vertices.map { it.value to it.key }).toMap())
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

    private fun nodeToParamName(queryState: QueryState, node: GraphQueryVertex) =
        "?${if (node in queryState.reverseCaptureGroups) queryState.reverseCaptureGroups[node] else node.name}"

    private fun mainRelationParameters(
        state: State,
        queryState: QueryState,
        query: GraphQuery
    ): NavigableMap<GraphQueryVertex, String> {
        val ret = TreeMap<GraphQueryVertex, String>()

        for (node in query.vertexSet().filterNot(query::isSunVertex))
            ret[node] = nodeToParamName(queryState, node)

        return ret
    }

    private fun compileQueryRepeated(state: State, queryState: QueryState, query: GraphQuery) {
        for (node in queryState.orderedKleeneVertices) {
            state.emitDecl(
                repeatedRelationName(queryState, node.name),
                listOf(
                    IDX_PARAM to NUMBER_TYPE,
                    REF_PARAM to NUMBER_TYPE,
                    nodeToParamName(queryState, node) to NUMBER_TYPE
                )
            )
            state.emitOutputDecl(repeatedRelationName(queryState, node.name))
        }

        val parsers = queryState.orderedKleeneVertices.associateWith {
            SouffleOutputParser(relationToPath(repeatedRelationName(queryState, it.name)))
        }

        for ((nodeIndex, node) in queryState.orderedKleeneVertices.withIndex()) {
            // Assumption: There is exactly 1 foreign key (is this just souffle or in general?)
            if (query.connectedVerticesOf(node).filter(query::isSunVertex).filterNot { it == node }.count() > 1)
                throw SouffleException("Broken assumption: ${node.name} is connected to more than one repeated vertex")

            val otherIndex =
                query.connectedVerticesOf(node)
                    .filter(query::isSunVertex)
                    .minOfOrNull(queryState.orderedKleeneVertices::indexOf) ?: nodeIndex
            val otherNode = if (nodeIndex <= otherIndex) null else queryState.orderedKleeneVertices[otherIndex]
            val valueParam = nodeToParamName(queryState, node)

            parsers[node]!!.addForeignReference()
            if (node in queryState.reverseCaptureGroups) {
                parsers[node]!!.addCaptureGroup(queryState.reverseCaptureGroups[node]!!, node)
            } else {
                parsers[node]!!.addVertex(node)
            }
            if (otherNode != null) {
                parsers[otherNode]!!.addSubparser(parsers[node]!!)
            } else {
                queryState.repeatedParsers.add(parsers[node]!!)
            }

            val vars = TreeMap<GraphQueryVertex, String>()

            state.emitRelation(
                souffle(repeatedRelationName(queryState, node.name), listOf(AUTOINC, REF_PARAM, valueParam)) {
                    if (otherNode == null || query.degreeOf(node) > 1) {
                        relation(mainRelationName(queryState)) {
                            if (otherNode == null)
                                param(REF_PARAM)
                            else
                                param(WILD)
                            for (mainParam in mainRelationParameters(state, queryState, query).keys) {
                                if (query.getEdge(node, mainParam) != null || query.getEdge(mainParam, node) != null) {
                                    param(mainParam.name)
                                    vars[mainParam] = mainParam.name
                                } else {
                                    param(WILD)
                                }
                            }
                        }
                    }
                    if (otherNode != null) {
                        relation(repeatedRelationName(queryState, otherNode.name)) {
                            param(REF_PARAM)
                            param(WILD)
                            param(otherNode.name)
                            vars[otherNode] = otherNode.name
                        }
                    }
                    nodeRelation(nodeToParamName(queryState, node), nodeRelationName(queryState, node.name))
                    for (e in query.incomingEdgesOf(node)) {
                        val v = query.getEdgeSource(e)
                        if (queryState.orderedKleeneVertices.indexOf(v) > nodeIndex)
                            continue
                        assert(v in vars)
                        edgeRelation(
                            vars[v]!!,
                            nodeToParamName(queryState, node),
                            edgeRelationName(queryState, v.name, node.name)
                        )
                    }
                    for (e in query.outgoingEdgesOf(node)) {
                        val v = query.getEdgeTarget(e)
                        if (queryState.orderedKleeneVertices.indexOf(v) > nodeIndex)
                            continue
                        assert(v in vars)
                        edgeRelation(
                            nodeToParamName(queryState, node),
                            vars[v]!!,
                            edgeRelationName(queryState, node.name, v.name)
                        )
                    }
                }
            )


        }
    }

    private fun compileQueryMain(state: State, queryState: QueryState, query: GraphQuery) {
        val vars = mainRelationParameters(state, queryState, query)

        state.emitDecl(
            mainRelationName(queryState), listOf(IDX_PARAM to NUMBER_TYPE) + vars.values.map { it to NUMBER_TYPE })
        state.emitOutputDecl(mainRelationName(queryState))

        val outputParser = SouffleOutputParser(relationToPath(mainRelationName(queryState)))

        for ((name, vertex) in queryState.captureGroups)
            if (vertex in vars)
                outputParser.addCaptureGroup(name, vertex)
        for (vertex in queryState.vertices.values)
            if (vertex in vars)
                outputParser.addVertex(vertex)
        for (subparser in queryState.repeatedParsers)
            outputParser.addSubparser(subparser)
        state.outputs.add(query to outputParser::parse)

        state.emitRelation(
            souffle(mainRelationName(queryState), listOf(AUTOINC) + vars.values) {
                for (node in vars.keys) {
                    when {
                        query.isKleeneVertex(node) -> TODO()
                        query.isSunVertex(node) -> assert(false) { "vars should not contain repeated nodes" }
                        else -> nodeRelation(vars[node]!!, nodeRelationName(queryState, node.name))
                    }
                }
                for (edge in query.edgeSet()) {
                    val src = query.getEdgeSource(edge)
                    val dst = query.getEdgeTarget(edge)
                    if (src !in vars || dst !in vars) {
                        assert(query.isSunVertex(src) || query.isSunVertex(dst))
                        continue
                    }
                    edgeRelation(vars[src]!!, vars[dst]!!, edgeRelationName(queryState, src.name, dst.name))
                }
            }
        )
    }

    private fun SouffleRelation.nodeRelation(nodeVar: String, relationName: String) {
        relation(NODE_RELATION) {
            param(nodeVar)
            param(WILD)
        }
        relation(relationName) { param(nodeVar) }
    }

    private fun SouffleRelation.edgeRelation(sourceVar: String, destinationVar: String, relationName: String) {
        relation(EDGE_RELATION) {
            param(sourceVar)
            param(destinationVar)
            param(WILD)
            param(WILD)
        }
        relation(relationName) {
            param(sourceVar)
            param(destinationVar)
        }
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

fun GraphQuery.isKleeneVertex(v: GraphQueryVertex) = this.incomingEdgesOf(v).any { e ->
    (e.mQuery as Metadata).options.any { it is MetadataOption.Kleene }
}

fun GraphQuery.isSunVertex(v: GraphQueryVertex) = (v.mQuery as Metadata).options.any { it is MetadataOption.Repeated }