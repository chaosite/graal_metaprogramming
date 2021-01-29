package il.ac.technion.cs.mipphd.graal.graphquery;

import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper;
import org.graalvm.compiler.graph.Node;

import java.util.function.Predicate;

public class GraphQueryVertexM extends GraphQueryVertex<Node> {
    private MQuery mQuery;
    public GraphQueryVertexM(MQuery mQuery) {
        super(Node.class, n -> mQuery.interpret(new NodeWrapper(n)));
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
        this.setPredicate(n -> this.mQuery.interpret(new NodeWrapper(n)));
    }

    @Override
    public String label() {
        return mQuery.serialize();
    }
}
