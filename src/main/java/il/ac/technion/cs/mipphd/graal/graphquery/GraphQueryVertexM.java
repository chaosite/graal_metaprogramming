package il.ac.technion.cs.mipphd.graal.graphquery;

import edu.umd.cs.findbugs.annotations.NonNull;
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper;
import org.graalvm.compiler.graph.Node;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class GraphQueryVertexM extends GraphQueryVertex<Node> {
    @NonNull
    private MQuery mQuery;

    public GraphQueryVertexM(@NotNull MQuery mQuery) {
        super(Node.class, n -> mQuery.interpret(new QueryTargetNode(new NodeWrapper(n))));
        this.mQuery = mQuery;
    }

    @NonNull
    public static GraphQueryVertexM fromQuery(@NonNull String query) {
        return new GraphQueryVertexM(MQueryKt.parseMQuery(query));
    }

    @NonNull
    public MQuery getMQuery() {
        return mQuery;
    }

    public void setMQuery(@NonNull MQuery mQuery) {
        this.mQuery = mQuery;
        this.setPredicate(n -> this.mQuery.interpret(new QueryTargetNode(new NodeWrapper(n))));
    }

    @Override
    @NonNull
    public String label() {
        return mQuery.serialize();
    }

    @Override
    @NonNull
    public Optional<String> captureGroup() {
        Metadata metadata = (Metadata) this.mQuery;
        return metadata.getOptions().stream()
                .filter(option -> option instanceof MetadataOption.CaptureName)
                .map(captureName -> ((MetadataOption.CaptureName) captureName).getName())
                .findAny();
    }

    @Override
    public String toString() {
        return "GraphQueryVertexM{" +
                "mQuery=" + mQuery.serialize() +
                '}';
    }
}
