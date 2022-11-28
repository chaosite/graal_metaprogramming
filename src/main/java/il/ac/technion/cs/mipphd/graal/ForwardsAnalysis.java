package il.ac.technion.cs.mipphd.graal;

import edu.umd.cs.findbugs.annotations.NonNull;
import il.ac.technion.cs.mipphd.graal.utils.GraalIRGraph;
import il.ac.technion.cs.mipphd.graal.utils.WrappedIRNodeImpl;

import java.util.List;

public abstract class ForwardsAnalysis<T> extends DataFlowAnalysis<T> {
    public ForwardsAnalysis(@NonNull GraalIRGraph graph, @NonNull List<WrappedIRNodeImpl> nodes, @NonNull List<WrappedIRNodeImpl> entryPoints, @NonNull List<WrappedIRNodeImpl> exitPoints) {
        super(graph, Direction.FORWARD, nodes, entryPoints, exitPoints);
    }
}
