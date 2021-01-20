package il.ac.technion.cs.mipphd.graal.graphquery;

import org.graalvm.compiler.nodes.*;
import org.graalvm.compiler.nodes.calc.ConditionalNode;

public class GraphMaker {
    public static GraphQuery createMaxGraph() {
        GraphQuery ret = new GraphQuery();

        GraphQueryVertex<Invoke> get = ret.addVertex(Invoke.class,
                invoke -> invoke.getTargetMethod().getDeclaringClass().toJavaName(true).equals("java.util.List") && invoke.getTargetMethod().getName().equals("get"));
        GraphQueryVertex<ValueNode> getKleene = ret.addVertex(ValueNode.class);
        GraphQueryVertex<ConditionalNode> conditional = ret.addVertex(ConditionalNode.class);
        GraphQueryVertex<LogicNode> conditionNode = ret.addVertex(LogicNode.class);
        GraphQueryVertex<PhiNode> returnValuePhi = ret.addVertex(PhiNode.class);
        GraphQueryVertex<ValueProxyNode> returnKleene = ret.addVertex(ValueProxyNode.class);
        GraphQueryVertex<ReturnNode> theReturn = ret.addVertex(ReturnNode.class);

        ret.addEdge(get, getKleene, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);
        ret.addEdge(getKleene, conditional, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.KLEENE);
        ret.addEdge(conditionNode, conditional);
        ret.addEdge(conditional, returnValuePhi, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);
        ret.addEdge(returnValuePhi, conditional, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);
        ret.addEdge(returnValuePhi, returnKleene, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);
        ret.addEdge(returnKleene, theReturn, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.KLEENE);

        return ret;
    }

    public static GraphQuery createMinimalQuery() {
        GraphQuery ret = new GraphQuery();

        ret.addVertex(Invoke.class,
                invoke -> invoke.getTargetMethod().getDeclaringClass().toJavaName(true).equals("java.util.List") && invoke.getTargetMethod().getName().equals("get"));
        return ret;
    }

    public static GraphQuery createSimpleKleeneQueryWithReturn() {
        GraphQuery ret = new GraphQuery();

        GraphQueryVertex<PhiNode> returnValuePhi = ret.addVertex(PhiNode.class);
        GraphQueryVertex<ValueProxyNode> returnKleene = ret.addVertex(ValueProxyNode.class);
        GraphQueryVertex<ReturnNode> theReturn = ret.addVertex(ReturnNode.class);

        ret.addEdge(returnValuePhi, returnKleene, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);
        ret.addEdge(returnKleene, theReturn, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.KLEENE);

        return ret;
    }

    public static GraphQuery createMinimalKleeneQuery() {
        GraphQuery ret = new GraphQuery();

        GraphQueryVertex<Invoke> get = ret.addVertex(Invoke.class,
                invoke -> invoke.getTargetMethod().getDeclaringClass().toJavaName(true).equals("java.util.List") && invoke.getTargetMethod().getName().equals("get"));
        GraphQueryVertex<ValueNode> getKleene = ret.addVertex(ValueNode.class);
        GraphQueryVertex<ConditionalNode> conditional = ret.addVertex(ConditionalNode.class);

        ret.addEdge(get, getKleene, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);
        ret.addEdge(getKleene, conditional, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.KLEENE);

        return ret;
    }

    public static GraphQuery createTwoVertexOneEdgeQuery() {
        GraphQuery ret = new GraphQuery();

        GraphQueryVertex<PhiNode> returnValuePhi = ret.addVertex(PhiNode.class);
        GraphQueryVertex<ConditionalNode> conditional = ret.addVertex(ConditionalNode.class);

        ret.addEdge(conditional, returnValuePhi, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);

        return ret;
    }

    public static GraphQuery createValueToReturnQuery() {
        GraphQuery ret = new GraphQuery();

        GraphQueryVertex<ValueNode> someNode = ret.addVertex(ValueNode.class);
        GraphQueryVertex<ValueNode> returnKleene = ret.addVertex(ValueNode.class, node -> !(node instanceof ReturnNode));
        GraphQueryVertex<ReturnNode> theReturn = ret.addVertex(ReturnNode.class);

        ret.addEdge(someNode, returnKleene, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);
        ret.addEdge(returnKleene, theReturn, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.KLEENE);

        return ret;
    }
}
