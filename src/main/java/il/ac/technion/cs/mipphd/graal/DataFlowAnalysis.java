package il.ac.technion.cs.mipphd.graal;

import edu.umd.cs.findbugs.annotations.NonNull;
import il.ac.technion.cs.mipphd.graal.utils.EdgeWrapper;
import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter;
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public abstract class DataFlowAnalysis<T> {
    @NonNull
    private final GraalAdapter graph;
    @NonNull
    private final List<NodeWrapper> nodes;
    @NonNull
    private final List<NodeWrapper> entryPoints;
    @NonNull
    private final List<NodeWrapper> exitPoints;
    @NonNull
    private final Direction direction;
    @NonNull
    protected Map<NodeWrapper, T> nodeToIn;
    @NonNull
    protected Map<NodeWrapper, T> nodeToOut;

    public DataFlowAnalysis(@NotNull GraalAdapter graph, @NotNull Direction direction, @NotNull List<NodeWrapper> nodes, @NotNull List<NodeWrapper> entryPoints, @NotNull List<NodeWrapper> exitPoints) {
        super();
        this.graph = graph;
        this.direction = direction;
        this.nodes = nodes;
        this.entryPoints = entryPoints;
        this.exitPoints = exitPoints;
        this.nodeToIn = new HashMap<>();
        this.nodeToOut = new HashMap<>();
    }

    public enum Direction {
        FORWARD,
        BACKWARD
    }

    protected abstract T newInitial();

    protected abstract void copy(T source, T dest);

    protected abstract void flow(T input, NodeWrapper d, T out);

    protected abstract void merge(T in1, T in2, T out);

    @NonNull
    public T getFlowAfter(@NonNull NodeWrapper node) {
        return nodeToOut.get(node);
    }

    @NonNull
    public T getFlowBefore(@NonNull NodeWrapper node) {
        return nodeToIn.get(node);
    }

    public void doAnalysis() {
        for (int iteration = 0; ; iteration++) {
            List<NodeWrapper> workingSet = getStartSet();
            boolean changed = false;
            HashSet<NodeWrapper> visited = new HashSet<>();
            while (!workingSet.isEmpty()) {
                final ArrayList<NodeWrapper> nextSet = new ArrayList<>();
                for (NodeWrapper w : workingSet) {
                    T before = nodeToOut.getOrDefault(w, newInitial());
                    T after = newInitial();
                    flow(mergePredecessors(w), w, after);
                    nodeToOut.put(w, after);
                    changed |= !before.equals(after);
                    visited.add(w);
                }

                for (NodeWrapper w : workingSet) {
                    nextSet.addAll(getLater(w).stream().filter(n -> !visited.contains(n)).collect(Collectors.toList()));
                }
                workingSet = nextSet;
            }
            System.out.println("Changed? " + changed);
            if (!changed)
                break;
        }
    }

    @NonNull
    private T mergePredecessors(@NonNull NodeWrapper node) {
        return getEarlier(node).stream()
                .map(p -> nodeToOut.getOrDefault(p, newInitial()))
                .reduce(newInitial(), (p, q) -> {
                    T out = newInitial();
                    merge(p, q, out);
                    return out;
                });
    }

    @NonNull
    private List<NodeWrapper> getStartSet() {
        switch (direction) {
            case FORWARD:
                return entryPoints;
            case BACKWARD:
                return exitPoints;
        }
        throw new RuntimeException();
    }

    private boolean controlEdgePredicate(@NonNull EdgeWrapper edge) {
        return edge.getLabel().equals(EdgeWrapper.CONTROL);
    }

    @NonNull
    private List<NodeWrapper> getEarlier(@NonNull NodeWrapper node) {
        switch (direction) {
            case FORWARD:
                return graph.incomingEdgesOf(node).stream().filter(this::controlEdgePredicate).map(graph::getEdgeSource).collect(Collectors.toList());
            case BACKWARD:
                return graph.outgoingEdgesOf(node).stream().filter(this::controlEdgePredicate).map(graph::getEdgeTarget).collect(Collectors.toList());
        }
        throw new RuntimeException();
    }

    @NonNull
    private List<NodeWrapper> getLater(@NonNull NodeWrapper node) {
        switch (direction) {
            case FORWARD:
                return graph.outgoingEdgesOf(node).stream().filter(this::controlEdgePredicate).map(graph::getEdgeTarget).collect(Collectors.toList());
            case BACKWARD:
                return graph.incomingEdgesOf(node).stream().filter(this::controlEdgePredicate).map(graph::getEdgeSource).collect(Collectors.toList());
        }
        throw new RuntimeException();
    }
}
