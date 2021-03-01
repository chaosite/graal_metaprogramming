package il.ac.technion.cs.mipphd.graal.utils;

import org.jgrapht.graph.DefaultEdge;

public class EdgeWrapper extends DefaultEdge {
    public static final String DATA = "DATA";
    public static final String CONTROL = "CONTROL";
    public static final String ASSOCIATED = "ASSOCIATED";
    protected final String label;
    protected final String name;

    public EdgeWrapper(String label, String name) {
        super();
        this.label = label;
        this.name = name;
    }

    public String getLabel() {
        assert(label.equals(DATA) || label.equals(CONTROL) || label.equals(ASSOCIATED));
        return label;
    }

    public String getName() {
        return name;
    }

    public String view() {
        return label + ": " + name;
    }

    @Override
    public String toString() {
        return "EdgeWrapper{" +
                "label='" + label + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
