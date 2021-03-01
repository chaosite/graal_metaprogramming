package il.ac.technion.cs.mipphd.graal.graphquery;

import java.util.Set;

public enum GraphQueryEdgeType {
    DATA_FLOW(Set.of("DATA")),
    CONTROL_FLOW(Set.of("CONTROL")),
    DATA_OR_CONTROL(Set.of("DATA", "CONTROL")),
    ASSOCIATED(Set.of("ASSOCIATED"));

    private final Set<String> matching;

    GraphQueryEdgeType(Set<String> matching) {
        this.matching = matching;
    }

    public boolean match(String s) {
        return matching.contains(s);
    }
}
