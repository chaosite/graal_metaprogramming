package il.ac.technion.cs.mipphd.graal.graphquery;

import il.ac.technion.cs.mipphd.graal.utils.EdgeWrapper;
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper;
import org.jgrapht.graph.DefaultEdge;

public class GraphQueryEdge extends DefaultEdge {
    protected MQuery mQuery;

    private static String buildQuery(GraphQueryEdgeType type, GraphQueryEdgeMatchType matchType) {
        String kleene;
        String query = "";
        if (type == GraphQueryEdgeType.CONTROL_FLOW || type == GraphQueryEdgeType.DATA_OR_CONTROL)
            query += "is('CONTROL')";
        if (type == GraphQueryEdgeType.DATA_OR_CONTROL)
            query += " and ";
        if (type == GraphQueryEdgeType.DATA_FLOW || type == GraphQueryEdgeType.DATA_OR_CONTROL)
            query += "is('DATA')";
        if (matchType == GraphQueryEdgeMatchType.KLEENE)
            kleene = "*|";
        else
            kleene = "";
        return kleene+query;
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

    public MQuery getMQuery() { return mQuery; }

    public void setMQuery(MQuery mQuery) { this.mQuery = mQuery; }

    public boolean match(NodeWrapper otherSource, EdgeWrapper otherEdge) {
        return getMQuery().interpret(new QueryTargetEdge(otherSource, otherEdge));
    }

    public String label() { return mQuery.serialize(); }
}
