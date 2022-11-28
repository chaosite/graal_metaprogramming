package il.ac.technion.cs.mipphd.graal.graphquery;

import org.jgrapht.graph.DefaultEdge;

public class GraphQueryEdge extends DefaultEdge {
    protected MQuery mQuery;

    private static String buildQuery(GraphQueryEdgeType type, GraphQueryEdgeMatchType matchType) {
        StringBuilder query = new StringBuilder();
        if (matchType == GraphQueryEdgeMatchType.KLEENE)
            query.append("*|");
        if (type == GraphQueryEdgeType.CONTROL_FLOW || type == GraphQueryEdgeType.DATA_OR_CONTROL)
            query.append("is('CONTROL')");
        if (type == GraphQueryEdgeType.DATA_OR_CONTROL)
            query.append(" and ");
        if (type == GraphQueryEdgeType.DATA_FLOW || type == GraphQueryEdgeType.DATA_OR_CONTROL)
            query.append("is('DATA')");

        return query.toString();
    }

    public static GraphQueryEdge fromQuery(String query) {
        return new GraphQueryEdge(MQueryKt.parseMQuery(query));
    }

    public GraphQueryEdge(GraphQueryEdgeType type, GraphQueryEdgeMatchType matchType) {
        this(MQueryKt.parseMQuery(buildQuery(type, matchType)));
    }

    public GraphQueryEdge(MQuery mQuery) {
        super();
        this.mQuery = mQuery;
    }

    public GraphQueryEdgeMatchType getMatchType() {
        if (!(mQuery instanceof Metadata))
            throw new RuntimeException("Expected top-level MQuery to be Metadata");
        final Metadata m = (Metadata) this.getMQuery();
        if (m.getOptions().contains(MetadataOption.Kleene.INSTANCE))
            return GraphQueryEdgeMatchType.KLEENE;
        return GraphQueryEdgeMatchType.NORMAL;
    }

    public MQuery getMQuery() {
        return mQuery;
    }

    public void setMQuery(MQuery mQuery) {
        this.mQuery = mQuery;
    }

    public boolean match(AnalysisNode otherSource, AnalysisEdge otherEdge) {
        return getMQuery().interpret(new QueryTargetEdge(otherSource, otherEdge));
    }

    public String label() {
        return mQuery.serialize();
    }
}
