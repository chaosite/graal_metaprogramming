package il.ac.technion.cs.mipphd.graal.graphquery;

import org.jgrapht.graph.DefaultEdge;

import java.util.function.Predicate;

public class GraphQueryEdge extends DefaultEdge {
    protected final GraphQueryEdgeType type;
    protected final GraphQueryEdgeMatchType matchType;
    protected final Predicate<Object> predicate;

    public GraphQueryEdge(GraphQueryEdgeType type, GraphQueryEdgeMatchType matchType, Predicate<Object> predicate) {
        super();
        this.type = type;
        this.matchType = matchType;
        this.predicate = predicate;
    }

    public GraphQueryEdgeType getType() {
        return type;
    }

    public GraphQueryEdgeMatchType getMatchType() {
        return matchType;
    }

    public Predicate<Object> getPredicate() {
        return predicate;
    }

    public String label() {
        StringBuilder sb = new StringBuilder();
        switch(type) {
            case CONTROL_FLOW:
                sb.append("CONTROL");
                break;
            case DATA_FLOW:
                sb.append("DATA");
                break;
            case BOTH:
                sb.append("BOTH");
                break;
            default:
                assert(false);
        }

        if (matchType == GraphQueryEdgeMatchType.KLEENE)
            sb.append("*");

        return sb.toString();
    }
}
