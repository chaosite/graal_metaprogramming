package il.ac.technion.cs.mipphd.graal.domains.psuedograph

data class MatchPoint(
    val queryVertexName: String,
    val query: String,
    val irVertexId: Int?,
    val vertexName: String
)

