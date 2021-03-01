package il.ac.technion.cs.mipphd.graal;

import edu.umd.cs.findbugs.annotations.NonNull;
import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter;
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper;

import java.util.List;

public abstract class ForwardsAnalysis<T> extends DataFlowAnalysis<T> {
    public ForwardsAnalysis(@NonNull GraalAdapter graph, @NonNull List<NodeWrapper> nodes, @NonNull List<NodeWrapper> entryPoints, @NonNull List<NodeWrapper> exitPoints) {
        super(graph, Direction.FORWARD, nodes, entryPoints, exitPoints);
    }
}
