package il.ac.technion.cs.mipphd.graal;

import il.ac.technion.cs.mipphd.graal.utils.CFGWrapper;
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedPseudograph;

public class GraalAdapter extends DirectedPseudograph<NodeWrapper, GraalAdapter.SimpleEdge> {
    public static class SimpleEdge extends DefaultEdge {
        final String label;

        SimpleEdge(String label) {
            super();
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public GraalAdapter() {
        super(() -> new NodeWrapper(null), () -> new SimpleEdge(""), false);
    }

    public static GraalAdapter fromGraal(CFGWrapper cfg) { return fromGraal(cfg.asCFG()); }

    public static GraalAdapter fromGraal(ControlFlowGraph cfg) {
        GraalAdapter g = new GraalAdapter();
        for (Node n : cfg.graph.getNodes()) {
            g.addVertex(new NodeWrapper(n)); // does not add duplicates
        }
        for (Node u : cfg.graph.getNodes()) {
            for (Node v : u.cfgSuccessors()) {
                g.addEdge(new NodeWrapper(u), new NodeWrapper(v), new SimpleEdge("CONTORL"));
            }
            for (Node v : u.usages()) {
                g.addEdge(new NodeWrapper(u), new NodeWrapper(v), new SimpleEdge("DATA"));
            }
        }
        return g;
    }
}
