package il.ac.technion.cs.mipphd.graal.utils;

import org.jgrapht.graph.DefaultEdge;

public class WrappedIREdge extends DefaultEdge {
    public static final String DATA = "DATA";
    public static final String CONTROL = "CONTROL";
    public static final String ASSOCIATED = "ASSOCIATED";
    protected final String kind;
    protected final String label;

    public WrappedIREdge(String kind, String label) {
        super();
        this.kind = kind;
        this.label = label;
    }

    public String getKind() {
        assert(kind.equals(DATA) || kind.equals(CONTROL) || kind.equals(ASSOCIATED));
        return kind;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return "EdgeWrapper{" +
                "kind='" + kind + '\'' +
                ", label='" + label + '\'' +
                '}';
    }
}
