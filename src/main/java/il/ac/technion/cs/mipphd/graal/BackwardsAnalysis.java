package il.ac.technion.cs.mipphd.graal;

import org.graalvm.compiler.nodes.cfg.Block;

import java.util.List;

public abstract class BackwardsAnalysis<T> extends DataFlowAnalysis<T> {
    public BackwardsAnalysis(List<Block> nodes, List<Block> entryPoints, List<Block> exitPoints) {
        super(Direction.BACKWARD, nodes, entryPoints, exitPoints);
    }
}
