package il.ac.technion.cs.mipphd.graal;

import edu.umd.cs.findbugs.annotations.NonNull;
import il.ac.technion.cs.mipphd.graal.utils.WrappedIREdge;
import il.ac.technion.cs.mipphd.graal.utils.GraalIRGraph;
import il.ac.technion.cs.mipphd.graal.utils.WrappedIRNodeImpl;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public abstract class DataFlowAnalysis<T> {
    @NonNull
    private final GraalIRGraph graph;
    @NonNull
    private final List<WrappedIRNodeImpl> nodes;
    @NonNull
    private final List<WrappedIRNodeImpl> entryPoints;
    @NonNull
    private final List<WrappedIRNodeImpl> exitPoints;
    @NonNull
    private final Direction direction;
    @NonNull
    protected Map<WrappedIRNodeImpl, T> nodeToIn;
    @NonNull
    protected Map<WrappedIRNodeImpl, T> nodeToOut;

    public DataFlowAnalysis(@NotNull GraalIRGraph graph, @NotNull Direction direction, @NotNull List<WrappedIRNodeImpl> nodes, @NotNull List<WrappedIRNodeImpl> entryPoints, @NotNull List<WrappedIRNodeImpl> exitPoints) {
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

    protected abstract void flow(T input, WrappedIRNodeImpl d, T out);

    protected abstract void merge(T in1, T in2, T out);

    @NonNull
    public T getFlowAfter(@NonNull WrappedIRNodeImpl node) {
        return nodeToOut.get(node);
    }

    @NonNull
    public T getFlowBefore(@NonNull WrappedIRNodeImpl node) {
        return nodeToIn.get(node);
    }

    public void doAnalysis() {
        for (int iteration = 0; ; iteration++) {
            List<WrappedIRNodeImpl> workingSet = getStartSet();
            boolean changed = false;
            HashSet<WrappedIRNodeImpl> visited = new HashSet<>();
            while (!workingSet.isEmpty()) {
                final ArrayList<WrappedIRNodeImpl> nextSet = new ArrayList<>();
                for (WrappedIRNodeImpl w : workingSet) {
                    T before = nodeToOut.getOrDefault(w, newInitial());
                    T after = newInitial();
                    flow(mergePredecessors(w), w, after);
                    nodeToOut.put(w, after);
                    changed |= !before.equals(after);
                    visited.add(w);
                }

                for (WrappedIRNodeImpl w : workingSet) {
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
    private T mergePredecessors(@NonNull WrappedIRNodeImpl node) {
        return getEarlier(node).stream()
                .map(p -> nodeToOut.getOrDefault(p, newInitial()))
                .reduce(newInitial(), (p, q) -> {
                    T out = newInitial();
                    merge(p, q, out);
                    return out;
                });
    }

    @NonNull
    private List<WrappedIRNodeImpl> getStartSet() {
        switch (direction) {
            case FORWARD:
                return entryPoints;
            case BACKWARD:
                return exitPoints;
        }
        throw new RuntimeException();
    }

    private boolean controlEdgePredicate(@NonNull WrappedIREdge edge) {
        return edge.getKind().equals(WrappedIREdge.CONTROL);
    }

    @NonNull
    private List<WrappedIRNodeImpl> getEarlier(@NonNull WrappedIRNodeImpl node) {
        switch (direction) {
            case FORWARD:
                return graph.incomingEdgesOf(node).stream().filter(this::controlEdgePredicate).map(graph::getEdgeSource).collect(Collectors.toList());
            case BACKWARD:
                return graph.outgoingEdgesOf(node).stream().filter(this::controlEdgePredicate).map(graph::getEdgeTarget).collect(Collectors.toList());
        }
        throw new RuntimeException();
    }

    @NonNull
    private List<WrappedIRNodeImpl> getLater(@NonNull WrappedIRNodeImpl node) {
        switch (direction) {
            case FORWARD:
                return graph.outgoingEdgesOf(node).stream().filter(this::controlEdgePredicate).map(graph::getEdgeTarget).collect(Collectors.toList());
            case BACKWARD:
                return graph.incomingEdgesOf(node).stream().filter(this::controlEdgePredicate).map(graph::getEdgeSource).collect(Collectors.toList());
        }
        throw new RuntimeException();
    }
}
