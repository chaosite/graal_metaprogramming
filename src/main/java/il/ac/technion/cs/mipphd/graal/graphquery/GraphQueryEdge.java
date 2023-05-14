package il.ac.technion.cs.mipphd.graal.graphquery;

import edu.umd.cs.findbugs.annotations.NonNull;
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

    @NonNull
    public static GraphQueryEdge fromQuery(@NonNull String query) {
        return new GraphQueryEdge(MQueryKt.parseMQuery(query));
    }

    public GraphQueryEdge(GraphQueryEdgeType type, GraphQueryEdgeMatchType matchType) {
        this(MQueryKt.parseMQuery(buildQuery(type, matchType)));
    }

    public GraphQueryEdge(MQuery mQuery) {
        super();

        this.mQuery = mQuery;
    }

    @NonNull
    public GraphQueryEdgeMatchType getMatchType() {
        if (!(mQuery instanceof Metadata))
            throw new RuntimeException("Expected top-level MQuery to be Metadata");
        final Metadata m = (Metadata) this.getMQuery();
        if (m.getOptions().contains(MetadataOption.Kleene.INSTANCE))
            return GraphQueryEdgeMatchType.KLEENE;
        else if (m.getOptions().contains(MetadataOption.Optional.INSTANCE)) {
            return GraphQueryEdgeMatchType.OPTIONAL;
        }
        return GraphQueryEdgeMatchType.NORMAL;
    }

    @NonNull
    public MQuery getMQuery() {
        return mQuery;
    }

    public void setMQuery(@NonNull MQuery mQuery) {
        assert(mQuery instanceof Metadata);
        this.mQuery = mQuery;
    }

    public boolean match(@NonNull AnalysisNode otherSource, @NonNull AnalysisEdge otherEdge) {
        return getMQuery().interpret(new QueryTargetEdge(otherSource, otherEdge));
    }

    @NonNull
    public String label() {
        return mQuery.serialize();
    }

    public boolean isKleene() { return ((Metadata) mQuery).getOptions().contains(MetadataOption.Kleene.INSTANCE); }
}
