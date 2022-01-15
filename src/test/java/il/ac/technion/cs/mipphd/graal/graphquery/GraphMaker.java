package il.ac.technion.cs.mipphd.graal.graphquery;

import il.ac.technion.cs.mipphd.graal.MethodToGraph;
import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter;
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper;
import org.graalvm.compiler.nodes.*;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
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
        g.exportQuery(writer, new ArrayList<NodeWrapper>());
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
                "  n1747810414 [ label=\"is('java.StoreIndexedNode')\" ];\n" +
                "  n1722105026 [ label=\"is('calc.AddNode')\" ];\n" +
                "  n1699268208 [ label=\"(?P<array>)|is('ValueNode')\" ];\n" +
                "  n1309175485 [ label=\"(?P<index>)|is('ValueNode')\" ];\n" +
                "  n1147572458 [ label=\"(?P<value>)|is('ValueNode')\" ];\n" +
                "  n1699268208 -> n1747810414 [ label=\"(is('DATA')) and ((name()) = ('array'))\" ];\n" +
                "  n1147572458 -> n1747810414 [ label=\"(is('DATA')) and ((name()) = ('value'))\" ];\n" +
                "  n1309175485 -> n1747810414 [ label=\"(is('DATA')) and ((name()) = ('index'))\" ];\n" +
                "  n1309175485 -> n1722105026 [ label=\"is('DATA')\" ];\n" +
                "  n1722105026 -> n1309175485 [ label=\"is('DATA')\" ];\n" +
                "}";
        return GraphQuery.importQuery(s, "Union");
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
                "  v1 [ label=\"is('LoopBeginNode')\" ];\n" +
                "  v2 [ label=\"not (is('IfNode'))\" ];\n" +
                "  v3 [ label=\"is('IfNode')\" ];\n" +
                "  v4 [ label=\"is('ValuePhiNode')\" ];\n" +
                "  v5 [ label=\"is('calc.AddNode')\" ];\n" +
                "  v6 [ label=\"is('calc.CompareNode')\" ];\n" +
                "  v7 [ label=\"is('ValueNode')\" ];\n" +
                "  v1 -> v2 [ label=\"*|is('CONTROL')\" ];\n" +
                "  v2 -> v3 [ label=\"is('CONTROL')\" ];\n" +
                "  v1 -> v4 [ label=\"(name()) = ('merge')\" ];\n" +
                "  v5 -> v4 [ label=\"is('DATA')\" ];\n" +
                "  v4 -> v5 [ label=\"is('DATA')\" ];\n" +
                "  v4 -> v6 [ label=\"is('DATA')\" ];\n" +
                "  v7 -> v4 [ label=\"not ((name()) = ('merge'))\" ];\n" +
                "}";
        return GraphQuery.importQuery(s, "LoopWithIterator");
    }
    public static GraphQuery LoopWithIteratorWithExtraStepsArrayDotQuery() {
        var s = "digraph G {\n" +
                "  v1 [ label=\"is('LoopBeginNode')\" ];\n" +
                "  v2 [ label=\"not (is('IfNode'))\" ];\n" +
                "  v3 [ label=\"is('IfNode')\" ];\n" +
                "  v4 [ label=\"is('ValuePhiNode')\" ];\n" +
                "  v5 [ label=\"is('calc.AddNode')\" ];\n" +
                "  v6 [ label=\"is('calc.CompareNode')\" ];\n" +
                "  v7 [ label=\"is('ValueNode')\" ];\n" +
                "  v8 [ label=\"(?P<iterator>)|is('java.LoadIndexedNode')\"]\n" +
                "  v9 [ label=\"(?P<source>)|is('ValueNode')\" ];\n" +
                "  v1 -> v2 [ label=\"*|is('CONTROL')\" ];\n" +
                "  v2 -> v3 [ label=\"is('CONTROL')\" ];\n" +
                "  v1 -> v4 [ label=\"(name()) = ('merge')\" ];\n" +
                "  v5 -> v4 [ label=\"is('DATA')\" ];\n" +
                "  v4 -> v5 [ label=\"is('DATA')\" ];\n" +
                "  v4 -> v6 [ label=\"is('DATA')\" ];\n" +
                "  v7 -> v4 [ label=\"not ((name()) = ('merge'))\" ];\n" +
                "  v4 -> v8 [ label=\"name() ='index'\" ]\n" +
                "  v9 -> v8 [ label=\"name() ='array'\" ]\n" +
                "}";
        return GraphQuery.importQuery(s, "LoopWithIterator");
    }

    public static GraphQuery LoopWithIteratorWithExtraStepsNoInitDotQuery() {
        var s = "digraph G {\n" +
                "  n667061891 [ label=\"is('LoopBeginNode')\" ];\n" +
                "  n462571646 [ label=\"not (is('IfNode'))\" ];\n" +
                "  n1384425610 [ label=\"is('IfNode')\" ];\n" +
                "  n783040301 [ label=\"is('ValuePhiNode')\" ];\n" +
                "  n670319422 [ label=\"is('calc.AddNode')\" ];\n" +
                "  n875080570 [ label=\"is('calc.CompareNode')\" ];\n" +
                "  n667061891 -> n462571646 [ label=\"*|is('CONTROL')\" ];\n" +
                "  n462571646 -> n1384425610 [ label=\"is('CONTROL')\" ];\n" +
                "  n667061891 -> n783040301 [ label=\"name() = 'merge'\" ];\n" +
                "  n670319422 -> n783040301 [ label=\"is('DATA')\" ];\n" +
                "  n783040301 -> n670319422 [ label=\"is('DATA')\" ];\n" +
                "  n783040301 -> n875080570 [ label=\"is('DATA')\" ];\n" +
                "}\n";
        return GraphQuery.importQuery(s);
    }

    public static GraphQuery PhiNodesIncDotQuery(){
        var s = "digraph G {\n" +
                "  n400359758 [ label=\"is('LoopBeginNode')\" ];\n" +
                "  n130451295 [ label=\"is('ValuePhiNode')\" ];\n" +
                "  n2020487576 [ label=\"is('calc.AddNode')\" ];\n" +
                "  n1679644407 [ label=\"is('ValueNode')\" ];\n" +
                "  n400359758 -> n130451295 [ label=\"(name()) = ('merge')\" ];\n" +
                "  n2020487576 -> n130451295 [ label=\"is('DATA')\" ];\n" +
                "  n130451295 -> n2020487576 [ label=\"is('DATA')\" ];\n" +
                "  n1679644407 -> n130451295 [ label=\"not ((name()) = ('merge'))\"  ];\n" +
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
        var s = "digraph G {\n" +
                "  v1 [ label=\"(?P<input>)|is('ValueNode')\" ];\n" +
                "  v3 [ label=\"(is('java.LoadFieldNode')) or (is('java.LoadIndexedNode'))\" ];\n" +
                "  v4 [ label=\"(?P<splitOutput1>)|(is('java.LoadFieldNode')) or (is('java.LoadIndexedNode'))\" ];\n" +
                "  v5 [ label=\"(is('java.LoadFieldNode')) or (is('java.LoadIndexedNode'))\" ];\n" +
                "  v6 [ label=\"(?P<splitOutput2>)|(is('java.LoadFieldNode')) or (is('java.LoadIndexedNode'))\" ];\n" +
                "  v7 [ label=\"not((is('java.LoadFieldNode')) or (is('java.LoadIndexedNode')))\"];\n" +
                "  v8 [ label=\"not((is('java.LoadFieldNode')) or (is('java.LoadIndexedNode')))\"];\n" +
                "  v1 -> v3 [ label=\"*|is('DATA')\" ];\n" +
                "  v1 -> v5 [ label=\"*|is('DATA')\" ];\n" +
                "  v3 -> v4 [ label=\"is('DATA')\" ];\n" +
                "  v5 -> v6 [ label=\"is('DATA')\" ];\n" +
                "  v6 -> v7 [ label=\"is('DATA')\" ];\n" +
                "  v4 -> v8 [ label=\"is('DATA')\" ];\n" +
                "}\n";

        return  GraphQuery.importQuery(s,"Split");
    }

    public static GraphQuery FunctionInvokeOneParamQuery(){
        GraphQuery ret = new GraphQuery();
        GraphQueryVertex<InvokeWithExceptionNode> invoke = ret.addVertex(InvokeWithExceptionNode.class);
        GraphQueryVertex<MethodCallTargetNode> methodcalltarget = ret.addVertex(MethodCallTargetNode.class);
        GraphQueryVertex<ValueNode> param = ret.addVertex(ValueNode.class);
        GraphQueryVertex<ValueNode> result = ret.addVertex(ValueNode.class);

        ret.addEdge(methodcalltarget, invoke, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);
        ret.addEdge(param, methodcalltarget, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);
        ret.addEdge(invoke, result, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);

        return  ret;
    }
    public static GraphQuery FunctionInvokeOneParamQueryDot(){
        var s = "\n" +
                "digraph G {\n" +
                "  n1973339225 [ label=\"(?P<invokeOutput>)|(is('InvokeWithExceptionNode') or is('InvokeNode'))\" ];\n" +
                "  n1149290751 [ label=\"is('java.MethodCallTargetNode')\" ];\n" +
                "  n276649967 [ label=\"(?P<invokeInput>)|is('ValueNode')\" ];\n" +
                "  n1707443639 [ label=\"is('ValueNode')\" ];\n" +
                "  n1149290751 -> n1973339225 [ label=\"is('DATA')\" ];\n" +
                "  n276649967 -> n1149290751 [ label=\"is('DATA')\" ];\n" +
                "  n1973339225 -> n1707443639 [ label=\"is('DATA')\" ];\n" +
                "}";
        return  GraphQuery.importQuery(s,"Function Invoke One Param");
    }

    public static GraphQuery IfWithConditionQuery(){
        GraphQuery ret = new GraphQuery();
        GraphQueryVertex<IfNode> ifNode = ret.addVertex(IfNode.class);
        GraphQueryVertex<CompareNode> cond = ret.addVertex(CompareNode.class);
        GraphQueryVertex<ValueNode> value1 = ret.addVertex(ValueNode.class);
        GraphQueryVertex<ValueNode> value2 = ret.addVertex(ValueNode.class);
        GraphQueryVertex<BeginNode> beginTrue = ret.addVertex(BeginNode.class);
        GraphQueryVertex<BeginNode> beginFalse = ret.addVertex(BeginNode.class);

        ret.addEdge(cond, ifNode, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);
        ret.addEdge(ifNode, beginTrue, GraphQueryEdgeType.CONTROL_FLOW, GraphQueryEdgeMatchType.NORMAL);
        ret.addEdge(ifNode, beginFalse, GraphQueryEdgeType.CONTROL_FLOW, GraphQueryEdgeMatchType.NORMAL);
        ret.addEdge(value1, cond, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);
        ret.addEdge(value2, cond, GraphQueryEdgeType.DATA_FLOW, GraphQueryEdgeMatchType.NORMAL);

        return  ret;
    }
    public static GraphQuery IfWithConditionQueryDot(){
        var s = "digraph G {\n" +
                "  n1581722373 [ label=\"is('IfNode')\" ];\n" +
                "  n401012039 [ label=\"is('calc.CompareNode')\" ];\n" +
                "  n551661064 [ label=\"(?P<ifInput1>)|is('ValueNode')\" ];\n" +
                "  n2060862812 [ label=\"(?P<ifInput2>)|is('ValueNode')\" ];\n" +
                "  n497964544 [ label=\"(?P<trueSuccessor>)|is('BeginNode')\" ];\n" +
                "  n1036178495 [ label=\"(?P<falseSuccessor>)|is('BeginNode')\" ];\n" +
                "  n401012039 -> n1581722373 [ label=\"is('DATA')\" ];\n" +
                "  n1581722373 -> n497964544 [ label=\"is('CONTROL') and (name() = 'trueSuccessor')\" ];\n" +
                "  n1581722373 -> n1036178495 [ label=\"is('CONTROL') and (name() = 'falseSuccessor')\" ];\n" +
                "  n551661064 -> n401012039 [ label=\"is('DATA') and (name() = 'x')\" ];\n" +
                "  n2060862812 -> n401012039 [ label=\"is('DATA') and (name() = 'y')\" ];\n" +
                "}\n";
        return  GraphQuery.importQuery(s, "If With Condition");
    }

    public static GraphQuery FunctionInvokeTwoParamInsideScopeQueryDot(){
        var s = "digraph G {\n" +
                "  n1697913910 [ label=\"(?P<scopeBranch>)|is('BeginNode')\" ];\n" +
                "  n687121738 [ label=\"is('ValueNode')\" ];\n" +
                "  n384996753 [ label=\"(?P<invokeScopeOutput>)|(is('InvokeWithExceptionNode')) or (is('InvokeNode'))\" ];\n" +
                "  n517854861 [ label=\"is('java.MethodCallTargetNode')\" ];\n" +
                "  n328134377 [ label=\"(?P<invokeScopeInput1>)|is('ValueNode')\" ];\n" +
                "  n299020052 [ label=\"(?P<invokeScopeInput2>)|is('ValueNode')\" ];\n" +
                "  n517854861 -> n384996753 [ label=\"is('DATA')\" ];\n" +
                "  n328134377 -> n517854861 [ label=\"is('DATA')\" ];\n" +
                "  n299020052 -> n517854861 [ label=\"is('DATA')\" ];\n" +
                "  n1697913910 -> n687121738 [ label=\"*|is('CONTROL')\" ];\n" +
                "  n687121738 -> n384996753 [ label=\"is('CONTROL')\" ];\n" +
                "}";
        return  GraphQuery.importQuery(s, "Function Invoke Two Param");
    }
    public static GraphQuery FunctionInvokeTwoParamInsideQueryDot(){
        var s = "digraph G {\n" +
                "  n1697913910 [ label=\"(?P<invoke2Output>)|(is('InvokeWithExceptionNode')) or (is('InvokeNode'))\" ];\n" +
                "  n384996753 [ label=\"is('java.MethodCallTargetNode')\" ];\n" +
                "  n1433997935 [ label=\"(?P<invoke2Input1>)|is('ValueNode')\" ];\n" +
                "  n517854861 [ label=\"(?P<invoke2Input2>)|is('ValueNode')\" ];\n" +
                "  n384996753 -> n1697913910 [ label=\"is('DATA')\" ];\n" +
                "  n1433997935 -> n384996753 [ label=\"is('DATA')\" ];\n" +
                "  n517854861 -> n384996753 [ label=\"is('DATA')\" ];\n" +
                "}";
        return  GraphQuery.importQuery(s, "Function Invoke Two Param");
    }

}
