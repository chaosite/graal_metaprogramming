package il.ac.technion.cs.mipphd.graal.graphquery;

import il.ac.technion.cs.mipphd.graal.MethodToGraph;
import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter;
import org.graalvm.compiler.nodes.*;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

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

        ret.addEdge(get, getKleene, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.KLEENE);
        ret.addEdge(getKleene, conditional, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);
        ret.addEdge(conditionNode, conditional);
        ret.addEdge(conditional, returnValuePhi, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);
        ret.addEdge(returnValuePhi, conditional, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);
        ret.addEdge(returnValuePhi, returnKleene, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.KLEENE);
        ret.addEdge(returnKleene, theReturn, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);

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

        ret.addEdge(returnValuePhi, returnKleene, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.KLEENE);
        ret.addEdge(returnKleene, theReturn, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);

        return ret;
    }

    public static GraphQuery createMinimalKleeneQuery() {
        GraphQuery ret = new GraphQuery();

        GraphQueryVertex<Invoke> get = ret.addVertex(Invoke.class,
                invoke -> invoke.getTargetMethod().getDeclaringClass().toJavaName(true).equals("java.util.List") && invoke.getTargetMethod().getName().equals("get"));
        GraphQueryVertex<ValueNode> getKleene = ret.addVertex(ValueNode.class);
        GraphQueryVertex<ConditionalNode> conditional = ret.addVertex(ConditionalNode.class);

        ret.addEdge(get, getKleene, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.KLEENE);
        ret.addEdge(getKleene, conditional, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);

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

        ret.addEdge(someNode, returnKleene, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.KLEENE);
        ret.addEdge(returnKleene, theReturn, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);

        return ret;
    }
    @Test
    public void graalGraph() throws NoSuchMethodException, FileNotFoundException, UnsupportedEncodingException {
        var methodToGraph = new MethodToGraph();
        var g = GraalAdapter.fromGraal(methodToGraph.getCFG(AlgorithmSample.class.getMethod("f2" )));
    }
    @Test
    public void exportAnonAgoReducedGraph() throws NoSuchMethodException, FileNotFoundException, UnsupportedEncodingException {
        var methodToGraph = new MethodToGraph();
        var writer = new PrintWriter("/home/dor/Desktop/PG/graal_reduced.dot", "UTF-8");
        var g = GraalAdapter.fromGraal(methodToGraph.getCFG(AlgorithmSample.class.getMethod("f2" )));
        g.simplify();
        g.removeLoopUnrolls();
        g.removeLeavesRec();
        g.exportQuery(writer, new ArrayList<>());
        writer.close();
    }
    public static GraalAdapter AnonGraphReduced() throws NoSuchMethodException {
        var methodToGraph = new MethodToGraph();
        var g = GraalAdapter.fromGraal(methodToGraph.getCFG(AlgorithmSample.class.getMethod("f2" )));
        g.simplify();
        g.removeLoopUnrolls();
        g.removeLeavesRec();
        return g;
    }
    public static GraphQuery UnionUsingArrayDotQuery(){
        var s = "digraph G {\n" +
                "  n1955913395 [ label=\"is('java.StoreIndexedNode')\" ];\n" +
                "  n1539904490 [ label=\"is('calc.AddNode')\" ];\n" +
                "  n1723898840 [ label=\"is('ValueNode')\" ];\n" +
                "  n2046053219 [ label=\"is('ValueNode')\" ];\n" +
                "  n734487464 [ label=\"(?P<value>)|is('ValueNode')\" ];\n" +
                "  n1723898840 -> n1955913395 [ label=\"is('DATA')\" ];\n" +
                "  n734487464 -> n1955913395 [ label=\"is('DATA')\" ];\n" +
                "  n2046053219 -> n1955913395 [ label=\"is('DATA')\" ];\n" +
                "  n2046053219 -> n1539904490 [ label=\"is('DATA')\" ];\n" +
                "  n1539904490 -> n2046053219 [ label=\"is('DATA')\" ];\n" +
                "}";
        return GraphQuery.importQuery(s);
    }
    public static GraphQuery UnionUsingArrayQuery(){
        GraphQuery ret = new GraphQuery();

        GraphQueryVertex<StoreIndexedNode> storeIndex = ret.addVertex(StoreIndexedNode.class);
        GraphQueryVertex<AddNode> add = ret.addVertex(AddNode.class);
        GraphQueryVertex<ValueNode> array = ret.addVertex(ValueNode.class);
        GraphQueryVertex<ValueNode> index = ret.addVertex(ValueNode.class);
        GraphQueryVertex<ValueNode> value = ret.addVertex(ValueNode.class);

        ret.addEdge(array,storeIndex,GraphQueryEdgeType.DATA_FLOW,GraphQueryEdgeMatchType.NORMAL);
        ret.addEdge(value,storeIndex,GraphQueryEdgeType.DATA_FLOW,GraphQueryEdgeMatchType.NORMAL);
        ret.addEdge(index,storeIndex,GraphQueryEdgeType.DATA_FLOW,GraphQueryEdgeMatchType.NORMAL);

        ret.addEdge(index,add,GraphQueryEdgeType.DATA_FLOW,GraphQueryEdgeMatchType.NORMAL);
        ret.addEdge(add,index,GraphQueryEdgeType.DATA_FLOW,GraphQueryEdgeMatchType.NORMAL);
        return ret;
    }

    public static GraphQuery LoopWithIteratorQuery(){
        GraphQuery ret = new GraphQuery();
        GraphQueryVertex<LoopBeginNode> loopBegin = ret.addVertex(LoopBeginNode.class);
        GraphQueryVertex<IfNode> ifNode = ret.addVertex(IfNode.class);
        GraphQueryVertex<ValuePhiNode> phi = ret.addVertex(ValuePhiNode.class);
        GraphQueryVertex<AddNode> add = ret.addVertex(AddNode.class);
        GraphQueryVertex<CompareNode> cmpNode = ret.addVertex(CompareNode.class);

        GraphQueryVertex<ValueNode> valueNode = ret.addVertex(ValueNode.class);

        ret.addEdge(loopBegin, ifNode);
        ret.addEdge(loopBegin, phi);
        ret.addEdge(add, phi);
        ret.addEdge(phi, add);
        ret.addEdge(phi, cmpNode);
        ret.addEdge(valueNode, phi);

        return  ret;
    }

    public static GraphQuery LoopWithIteratorWithExtraStepsDotQuery() {
        var s = "digraph G {\n" +
                "  n1581722373 [ label=\"is('LoopBeginNode')\" ];\n" +
                "  n401012039 [ label=\"not(is('IfNode'))\" ];\n" +
                "  n551661064 [ label=\"is('IfNode')\" ];\n" +
                "  n2060862812 [ label=\"is('ValuePhiNode')\" ];\n" +
                "  n497964544 [ label=\"is('calc.AddNode')\" ];\n" +
                "  n1036178495 [ label=\"is('calc.CompareNode')\" ];\n" +
                "  n285125729 [ label=\"is('ValueNode')\" ];\n" +
                "  n1581722373 -> n401012039 [ label=\"*|is('CONTROL')\" ];\n" +
                "  n401012039 -> n551661064 [ label=\"is('CONTROL')\" ];\n" +
                "  n1581722373 -> n2060862812 [ label=\"(1) = (1)\" ];\n" +
                "  n497964544 -> n2060862812 [ label=\"(1) = (1)\" ];\n" +
                "  n2060862812 -> n497964544 [ label=\"(1) = (1)\" ];\n" +
                "  n2060862812 -> n1036178495 [ label=\"(1) = (1)\" ];\n" +
                "  n285125729 -> n2060862812 [ label=\"(1) = (1)\" ];\n" +
                "}";
        return GraphQuery.importQuery(s);
    }

    public static GraphQuery PhiNodesIncDotQuery(){
        var s = "digraph G {\n" +
                "  n674421699 [ label=\"is('LoopBeginNode')\" ];\n" +
                "  n1322240894 [ label=\"is('ValuePhiNode')\" ];\n" +
                "  n202106861 [ label=\"is('calc.AddNode')\" ];\n" +
                "  n1969077088 [ label=\"is('ValueNode')\" ];\n" +
                "  n674421699 -> n1322240894 [ label=\"(1) = (1)\" ];\n" +
                "  n202106861 -> n1322240894 [ label=\"(1) = (1)\" ];\n" +
                "  n1322240894 -> n202106861 [ label=\"(1) = (1)\" ];\n" +
                "  n1969077088 -> n1322240894 [ label=\"(1) = (1)\" ];\n" +
                "}";
        return GraphQuery.importQuery(s);
    }

    public static GraphQuery LoopWithIteratorWithExtraStepsQuery(){
        GraphQuery ret = new GraphQuery();
        GraphQueryVertex<LoopBeginNode> loopBegin = ret.addVertex(LoopBeginNode.class);
        GraphQueryVertex<IfNode> step = ret.addVertex(IfNode.class);
        GraphQueryVertex<IfNode> ifNode = ret.addVertex(IfNode.class);
        GraphQueryVertex<ValuePhiNode> phi = ret.addVertex(ValuePhiNode.class);
        GraphQueryVertex<AddNode> add = ret.addVertex(AddNode.class);
        GraphQueryVertex<CompareNode> cmpNode = ret.addVertex(CompareNode.class);

        GraphQueryVertex<ValueNode> valueNode = ret.addVertex(ValueNode.class);

        ret.addEdge(loopBegin, step, GraphQueryEdgeType.CONTROL_FLOW, GraphQueryEdgeMatchType.KLEENE);
        ret.addEdge(step, ifNode, GraphQueryEdgeType.CONTROL_FLOW, GraphQueryEdgeMatchType.NORMAL);
        ret.addEdge(loopBegin, phi);
        ret.addEdge(add, phi);
        ret.addEdge(phi, add);
        ret.addEdge(phi, cmpNode);
        ret.addEdge(valueNode, phi);

        return  ret;
    }

    public static GraphQuery SplitNodePairQuery(){
        GraphQuery ret = new GraphQuery();
        GraphQueryVertex<ValueNode> start = ret.addVertex(ValueNode.class);
        GraphQueryVertex<LoadFieldNode> loadField1 = ret.addVertex(LoadFieldNode.class);
        GraphQueryVertex<LoadIndexedNode> loadField1_end = ret.addVertex(LoadIndexedNode.class);
        GraphQueryVertex<LoadFieldNode> loadField2 = ret.addVertex(LoadFieldNode.class);
        GraphQueryVertex<LoadFieldNode> loadField2_end = ret.addVertex(LoadFieldNode.class);

        ret.addEdge(start, loadField1, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.KLEENE);
        ret.addEdge(start, loadField2, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.KLEENE);
        ret.addEdge(loadField1, loadField1_end, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);
        ret.addEdge(loadField2, loadField2_end, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);

        return  ret;
    }
    public static GraphQuery SplitNodePairDotQuery(){
        //this looks for phi node but probably should start from any node?
        var s = "digraph G {\n" +
                "  n17353120 [ label=\"is('ValuePhiNode')\" ];\n" +
                "  n1519297134 [ label=\"(is('java.LoadFieldNode')) or (is('java.LoadIndexedNode'))\" ];\n" +
                "  n1305509131 [ label=\"is('java.LoadFieldNode')\" ];\n" +
                "  n1537767148 [ label=\"(is('java.LoadFieldNode')) or (is('java.LoadIndexedNode'))\" ];\n" +
                "  n472572400 [ label=\"is('java.LoadFieldNode')\" ];\n" +
                "  n17353120 -> n1519297134 [ label=\"*|is('DATA')\" ];\n" +
                "  n17353120 -> n1537767148 [ label=\"*|is('DATA')\" ];\n" +
                "  n1519297134 -> n1305509131 [ label=\"is('DATA')\" ];\n" +
                "  n1537767148 -> n472572400 [ label=\"is('DATA')\" ];\n" +
                "}";

        return  GraphQuery.importQuery(s);
    }
    public static GraphQuery SplitCollectionIndexedPairDotQuery(){
        //this looks for phi node but probably should start from any node?
        var s = "digraph G {\n" +
                "  n585494062 [ label=\"is('ValuePhiNode')\" ];\n" +
                "  n1 [ label=\"(?P<collection>)|is('java.LoadIndexedNode')\" ];\n" +
                "  n1799105301 [ label=\"(is('java.LoadFieldNode')) or (is('java.LoadIndexedNode'))\" ];\n" +
                "  n1699268208 [ label=\"is('java.LoadFieldNode')\" ];\n" +
                "  n1309175485 [ label=\"(is('java.LoadFieldNode')) or (is('java.LoadIndexedNode'))\" ];\n" +
                "  n1147572458 [ label=\"is('java.LoadFieldNode')\" ];\n" +
                "  n585494062 -> n1 [ label=\"is('DATA')\" ];\n" +
                "  n1 -> n1799105301 [ label=\"*|is('DATA')\" ];\n" +
                "  n1 -> n1309175485 [ label=\"*|is('DATA')\" ];\n" +
                "  n1799105301 -> n1699268208 [ label=\"is('DATA')\" ];\n" +
                "  n1309175485 -> n1147572458 [ label=\"is('DATA')\" ];\n" +
                "}\n";

        return  GraphQuery.importQuery(s);
    }

    public static GraphQuery CombinedQuery(){
        var s = "digraph G {\n" +
                "  n1869583419 [ label=\"is('LoopBeginNode')\" ];\n" +
                "  n110972948 [ label=\"is('ValuePhiNode')\" ];\n" +
                "  n364954288 [ label=\"is('calc.AddNode')\" ];\n" +
                "  n759844280 [ label=\"is('ValueNode')\" ];\n" +
                "  n1869583419 -> n110972948 [ label=\"(1) = (1)\" ];\n" +
                "  n364954288 -> n110972948 [ label=\"(1) = (1)\" ];\n" +
                "  n110972948 -> n364954288 [ label=\"(1) = (1)\" ];\n" +
                "  n759844280 -> n110972948 [ label=\"(1) = (1)\" ];\n" +
                "  \n" +
                "    n307119154 [ label=\"is('ValuePhiNode')\" ];\n" +
                "  n1635516920 [ label=\"(?P<collection>)|is('java.LoadIndexedNode')\" ];\n" +
                "  n1689230184 [ label=\"(is('java.LoadFieldNode')) or (is('java.LoadIndexedNode'))\" ];\n" +
                "  n2051054850 [ label=\"is('java.LoadFieldNode')\" ];\n" +
                "  n1713270341 [ label=\"(is('java.LoadFieldNode')) or (is('java.LoadIndexedNode'))\" ];\n" +
                "  n2129845557 [ label=\"is('java.LoadFieldNode')\" ];\n" +
                "  n307119154 -> n1635516920 [ label=\"is('DATA')\" ];\n" +
                "  n1635516920 -> n1689230184 [ label=\"*|is('DATA')\" ];\n" +
                "  n1635516920 -> n1713270341 [ label=\"*|is('DATA')\" ];\n" +
                "  n1689230184 -> n2051054850 [ label=\"is('DATA')\" ];\n" +
                "  n1713270341 -> n2129845557 [ label=\"is('DATA')\" ];\n" +
                "  \n" +
                "  n250665002 [ label=\"is('java.StoreIndexedNode')\" ];\n" +
                "  n967293828 [ label=\"is('calc.AddNode')\" ];\n" +
                "  n832443666 [ label=\"is('ValueNode')\" ];\n" +
                "  n368309743 [ label=\"is('ValueNode')\" ];\n" +
                "  n2031310144 [ label=\"(?P<value>)|is('ValueNode')\" ];\n" +
                "  n832443666 -> n250665002 [ label=\"is('DATA')\" ];\n" +
                "  n2031310144 -> n250665002 [ label=\"is('DATA')\" ];\n" +
                "  n368309743 -> n250665002 [ label=\"is('DATA')\" ];\n" +
                "  n368309743 -> n967293828 [ label=\"is('DATA')\" ];\n" +
                "  n967293828 -> n368309743 [ label=\"is('DATA')\" ];\n" +
                "  \n" +
                "  n307119154 -> n110972948 [label=\"*|is('DATA')\"];\n" +
                "  n1635516920 -> n2031310144 [label=\"*|is('DATA')\"];\n" +
                "}\n";
        return  GraphQuery.importQuery(s);
    }
}
