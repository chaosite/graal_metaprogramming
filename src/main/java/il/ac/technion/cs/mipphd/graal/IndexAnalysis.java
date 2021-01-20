package il.ac.technion.cs.mipphd.graal;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.spi.Proxy;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class IndexAnalysis extends BackwardsAnalysis<HashSet<Node>> {
    private List<Invoke> results = new ArrayList<>();

    public IndexAnalysis(List<Block> nodes, List<Block> entryPoints, List<Block> exitPoints) {
        super(nodes, entryPoints, exitPoints);
    }

    public List<Invoke> getResults() {
        return results;
    }

    @Override
    protected HashSet<Node> newInitial() {
        return new HashSet<>();
    }

    @Override
    protected void copy(HashSet<Node> source, HashSet<Node> dest) {
        dest.clear();
        dest.addAll(source);
    }

    @Override
    protected void merge(HashSet<Node> in1, HashSet<Node> in2, HashSet<Node> out) {
        out.clear();
        out.addAll(in1);
        out.addAll(in2);
    }

    @Override
    protected void flow(HashSet<Node> input, Block d, HashSet<Node> out) {
        out.addAll(input);
        List<Node> nodes = StreamSupport.stream(d.getNodes().spliterator(), false).collect(Collectors.toList());
        Collections.reverse(nodes);
        for (Node n : nodes) {
            if (n instanceof ReturnNode) {
                System.out.println(n);
                Collection<Node> results = derefProxies(((ReturnNode) n).result()).stream().filter(i -> !(i instanceof ConstantNode)).collect(Collectors.toUnmodifiableList());
                out.addAll(results);
                System.out.println(out);
            }
            if (out.contains(n)) {

                if (n instanceof Invoke) {
                    out.addAll(derefProxies(((Invoke) n).getReceiver()));
                    out.addAll(((Invoke) n).callTarget().arguments().stream().map(this::derefProxies).flatMap(Collection::stream).collect(Collectors.toList()));
                    ResolvedJavaMethod method = ((Invoke) n).getTargetMethod();
                    if (method.getDeclaringClass().toJavaName(true).equals("java.util.List") && method.getName().equals("get")) {
                        System.out.println("Done?");
                        results.add((Invoke) n);
                    }
                    System.out.println(out);
                } else if (n instanceof LoadFieldNode) {
                    /* ?? */
                } else {
                    System.out.println("Unhandled type: " + n.getClass().getName());
                }
            }
        }
    }

    private Collection<Node> derefProxies(Node node) {
        if (node instanceof Proxy)
            return derefProxies(((Proxy) node).getOriginalNode());
        else if (node instanceof ValuePhiNode) {
            ArrayList<Node> ret = new ArrayList<>();
            for (Node phiValue : ((ValuePhiNode) node).values()) {
                ret.addAll(derefProxies(phiValue));
            }
            return ret;
        }
        return List.of(node);
    }
}
