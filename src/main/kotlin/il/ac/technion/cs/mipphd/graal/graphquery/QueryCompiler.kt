package il.ac.technion.cs.mipphd.graal.graphquery

typealias QueryResults = List<Map<GraphQueryVertex, List<AnalysisNode>>>
interface CompiledQuery {
    fun execute(graph: AnalysisGraph): Map<GraphQuery, QueryResults>
}
interface QueryCompiler {
    fun compile(queries: List<GraphQuery>): CompiledQuery
}