package il.ac.technion.cs.mipphd.graal.utils;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;

import java.util.Objects;

public class CFGWrapper {
    @NonNull
    private final ControlFlowGraph cfg;

    public CFGWrapper(ControlFlowGraph cfg) {
        this.cfg = cfg;
    }

    @NonNull
    public ControlFlowGraph asCFG() { return cfg; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CFGWrapper that = (CFGWrapper) o;
        return cfg.equals(that.cfg);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cfg);
    }
}
