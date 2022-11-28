package il.ac.technion.cs.mipphd.graal.utils;

public class WrappedIRPhiEdge extends WrappedIREdge {
    private final WrappedIRNodeImpl from;
    public WrappedIRPhiEdge(String kind, String label, WrappedIRNodeImpl from) {
        super(kind, label);
        this.from = from;
    }

    public WrappedIRNodeImpl getFrom() {
        return from;
    }
}
