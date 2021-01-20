package il.ac.technion.cs.mipphd.graal.graphquery;

import java.util.Set;

public enum GraphQueryEdgeType {
    DATA_FLOW(Set.of("DATA")),
    CONTROL_FLOW(Set.of("CONTROL")),
    BOTH(Set.of("DATA", "CONTROL"));

    private final Set<String> matching;

    GraphQueryEdgeType(Set<String> matching) {
        this.matching = matching;
    }

    public boolean match(String s) {
        return matching.contains(s);
    }
}
