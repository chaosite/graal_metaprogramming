package il.ac.technion.cs.mipphd.graal.graphquery;

import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper;
import org.graalvm.compiler.graph.Node;

import java.util.Optional;

public class GraphQueryVertexM extends GraphQueryVertex<Node> {
    private MQuery mQuery;
    public GraphQueryVertexM(MQuery mQuery) {
        super(Node.class, n -> mQuery.interpret(new QueryTargetNode(new NodeWrapper(n))));
        this.mQuery = mQuery;
    }

    public static GraphQueryVertexM fromQuery(String query) {
        return new GraphQueryVertexM(MQueryKt.parseMQuery(query));
    }

    public MQuery getMQuery() {
        return mQuery;
    }

    public void setMQuery(MQuery mQuery) {
        this.mQuery = mQuery;
        this.setPredicate(n -> this.mQuery.interpret(new QueryTargetNode(new NodeWrapper(n))));
    }

    @Override
    public String label() {
        return mQuery.serialize();
    }

    @Override
    public Optional<String> captureGroup() {
        Metadata metadata = (Metadata) this.mQuery;
        return metadata.getOptions().stream()
                .filter(option -> option instanceof MetadataOption.CaptureName)
                .map(captureName -> ((MetadataOption.CaptureName) captureName).getName())
                .findAny();
    }
}
