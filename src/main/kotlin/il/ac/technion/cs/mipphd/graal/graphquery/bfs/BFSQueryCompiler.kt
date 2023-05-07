package il.ac.technion.cs.mipphd.graal.graphquery.bfs

import il.ac.technion.cs.mipphd.graal.graphquery.*

class CompiledBFSQuery(private val queries: List<GraphQuery>) : CompiledQuery {
    override fun execute(graph: AnalysisGraph): Map<GraphQuery, QueryResults>
        = queries.associateWith { it.match(graph) }
}

class BFSQueryCompiler : QueryCompiler {
    override fun compile(queries: List<GraphQuery>): CompiledQuery = CompiledBFSQuery(queries)
}