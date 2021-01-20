package il.ac.technion.cs.mipphd.graal;

import org.graalvm.compiler.nodes.cfg.Block;

import java.util.*;
import java.util.stream.Collectors;

public abstract class DataFlowAnalysis<T> {
    private final List<Block> nodes;
    private final List<Block> entryPoints;
    private final List<Block> exitPoints;
    private final Direction direction;
    protected Map<Block, T> nodeToIn;
    protected Map<Block, T> nodeToOut;

    public DataFlowAnalysis(Direction direction, List<Block> nodes, List<Block> entryPoints, List<Block> exitPoints) {
        this.direction = direction;
        this.nodes = nodes;
        this.entryPoints = entryPoints;
        this.exitPoints = exitPoints;
        this.nodeToIn = new HashMap<>();
        this.nodeToOut = new HashMap<>();
    }

    public enum Direction {
        FORWARD,
        BACKWARD;
    }

    protected abstract T newInitial();

    protected abstract void copy(T source, T dest);

    protected abstract void flow(T input, Block d, T out);

    protected abstract void merge(T in1, T in2, T out);



    public T getFlowAfter(Block node) {
        return nodeToOut.get(node);
    }

    public T getFlowBefore(Block node) {
        return nodeToIn.get(node);
    }

    public void doAnalysis() {
        for (int iteration = 0; ; iteration++) {
            List<Block> workingSet = getStartSet();
            boolean changed = false;
            HashSet<Block> visited = new HashSet<>();
            while (!workingSet.isEmpty()) {
                final ArrayList<Block> nextSet = new ArrayList<>();
                for (Block w : workingSet) {
                    T before = nodeToOut.getOrDefault(w, newInitial());
                    T after = newInitial();
                    flow(mergePredecessors(w), w, after);
                    nodeToOut.put(w, after);
                    changed |= !before.equals(after);
                    visited.add(w);
                }

                for (Block w : workingSet) {
                    nextSet.addAll(getLater(w).stream().filter(n -> !visited.contains(n)).collect(Collectors.toList()));
                }
                workingSet = nextSet;
            }
            System.out.println("Changed? " + changed);
            if (!changed)
                break;
        }
    }

    private T mergePredecessors(Block node) {
        return getEarlier(node).stream()
                .map(p -> nodeToOut.getOrDefault(p, newInitial()))
                .reduce(newInitial(), (p, q) -> {
                    T out = newInitial();
                    merge(p, q, out);
                    return out;
                });
    }

    private List<Block> getStartSet() {
        switch (direction) {
            case FORWARD:
                return entryPoints;
            case BACKWARD:
                return exitPoints;
        }
        throw new RuntimeException();
    }

    private List<Block> getEarlier(Block node) {
        switch (direction) {
            case FORWARD:
                return Arrays.asList(node.getPredecessors());
            case BACKWARD:
                return Arrays.asList(node.getSuccessors());
        }
        throw new RuntimeException();
    }

    private List<Block> getLater(Block node) {
        switch (direction) {
            case FORWARD:
                return Arrays.asList(node.getSuccessors());
            case BACKWARD:
                return Arrays.asList(node.getPredecessors());
        }
        throw new RuntimeException();
    }
}
