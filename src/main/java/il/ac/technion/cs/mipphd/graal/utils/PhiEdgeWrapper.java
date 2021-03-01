package il.ac.technion.cs.mipphd.graal.utils;

public class PhiEdgeWrapper extends EdgeWrapper {
    private final NodeWrapper from;
    public PhiEdgeWrapper(String label, String name, NodeWrapper from) {
        super(label, name);
        this.from = from;
    }

    public NodeWrapper getFrom() {
        return from;
    }
}
