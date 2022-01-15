package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.Listable
import il.ac.technion.cs.mipphd.graal.MethodToGraph
import il.ac.technion.cs.mipphd.graal.graphquery.GraphMaker.*
import org.graalvm.compiler.graph.NodeInterface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.PrintWriter
import java.io.StringReader
import java.io.StringWriter
import kotlin.reflect.jvm.javaMethod

import java.util.*


val maximumQueryText = """
    digraph G {
      n2021302077 [ label="is('Invoke') and method().name = 'get' and method().className = 'java.util.List'" ];
      n1948571966 [ label="is('ValueNode')" ];
      n1689892436 [ label="is('calc.ConditionalNode')" ];
      n818409230 [ label="is('LogicNode')" ];
      n484969820 [ label="is('PhiNode')" ];
      n1314376406 [ label="is('ValueProxyNode')" ];
      n1744166494 [ label="is('ReturnNode')" ];
      n2021302077 -> n1948571966 [ label="*|is('DATA')" ];
      n1948571966 -> n1689892436 [ label="is('DATA')" ];
      n818409230 -> n1689892436 [ label="is('DATA') or is('CONTROL')" ];
      n1689892436 -> n484969820 [ label="is('DATA')" ];
      n484969820 -> n1689892436 [ label="is('DATA')" ];
      n484969820 -> n1314376406 [ label="*|is('DATA')" ];
      n1314376406 -> n1744166494 [ label="is('DATA')" ];
    }
""".trimIndent()

val repeatedNodesQueryText = """
    digraph G {
    	framestate [ label="is('FrameState')"];
    	merge [ label="1 = 1"]
    	values [ label="[]|1 = 1"]

    	values -> framestate [ label = "is('DATA') and name() = 'values'"];
    	framestate -> merge [ label = "is('DATA') and name() = 'stateAfter'"];
    }
""".trimIndent()

internal class GraphQueryTest {
    val methodToGraph = MethodToGraph()
    val maximum = Listable::maximum.javaMethod
    val maximumQuery = GraphMaker.createMaxGraph()

    @Test
    fun `all nodes in query match some node in actual graph`() {
        val cfg = methodToGraph.getCFG(maximum)
        val set = mutableSetOf(*cfg.asCFG().graph.nodes.toList().toTypedArray())
        maximumQuery.vertexSet().forEach { queryNode ->
            assertTrue(set.any { (queryNode as GraphQueryVertex<*>).match(it) }, "Couldn't find $queryNode")
        }
    }

    @Test
    fun `maximum query matches maximum function ir`() {
        val cfg = methodToGraph.getCFG(maximum)
        assertEquals(18, maximumQuery.match(cfg).size)
    }

    @Test
    fun `minimal query matches maximum function ir`() {
        val cfg = methodToGraph.getCFG(maximum)
        assertEquals(2, GraphMaker.createMinimalQuery().match(cfg).size)
    }

    @Test
    fun `repeated query matches maximum function ir`() {
        val cfg = methodToGraph.getCFG(maximum)
        val results = GraphQuery.importQuery(repeatedNodesQueryText).match(cfg)

        println(results)

        assertEquals(19, results.size)
    }

    @Test
    fun `two vertex one edge query matches maximum function ir`() {
        val cfg = methodToGraph.getCFG(maximum)
        assertEquals(2, GraphMaker.createTwoVertexOneEdgeQuery().match(cfg).size)
    }

    @Test
    fun `export does not throw`() {
        val writer = StringWriter()
        GraphMaker.createMaxGraph().exportQuery(writer)
        println(writer.buffer.toString())
    }

    @Test
    fun `export-import round trip does not throw`() {
        val writer = StringWriter()
        GraphMaker.createMaxGraph().exportQuery(writer)
        val exported = writer.buffer.toString()
        val reader = StringReader(exported)
        GraphQuery.importQuery(reader)
    }

    @Test
    fun `imported maximum query matches maximum function ir`() {
        val cfg = methodToGraph.getCFG(maximum)
        val query = GraphQuery.importQuery(StringReader(maximumQueryText))


        val writer = StringWriter()
        query.exportQuery(writer)
        println(writer.buffer.toString())

        val set = mutableSetOf(*cfg.asCFG().graph.nodes.toList().toTypedArray())
        query.vertexSet().forEach { queryNode ->
            assertTrue(set.any { (queryNode as GraphQueryVertex<NodeInterface>).match(it) }, "Couldn't find $queryNode")
        }
        assertEquals(18, query.match(cfg).size)
    }

    @Test
    fun `imported anon graph query union`() {
        val cfg = AnonGraphReduced();
        val query = UnionUsingArrayDotQuery();
        val strwriter = StringWriter()
        query.exportQuery(strwriter)
        println(strwriter)
        val results = query.match(cfg);
        val path = "/home/dor/Desktop/PG/dots/unionquery/"
        results.forEachIndexed { i, r ->
            val writer = PrintWriter("$path$i.dot", "UTF-8")
            cfg.exportQuery(writer, query, r.toMap());
        }
    }


    @Test
    fun `imported anon graph query loop with index and loadfield`() {
        val cfg = AnonGraphReduced();
        val query = LoopWithIteratorWithExtraStepsDotQuery();
        val results = query.match(cfg);
        val path = "/home/dor/Desktop/PG/dots/loops/"
        val strwriter = StringWriter()
        query.exportQuery(strwriter)
        println(strwriter)
        results.forEachIndexed { i, r ->
            val writer = PrintWriter("$path$i.dot", "UTF-8")
            cfg.exportQuery(writer, query, r.toMap());
        }
    }

    @Test
    fun `imported anon graph query loop with index and array access`() {
        val cfg = AnonGraphReduced();
        val query = LoopWithIteratorWithExtraStepsArrayDotQuery();
        val results = query.matchPorts(cfg);
        val path = "/home/dor/Desktop/PG/dots/loopsWithArray/"
        val strwriter = StringWriter()
        query.exportQuery(strwriter)
        println(strwriter)
        results.values.forEachIndexed { i, r ->
            val writer = PrintWriter("$path$i.dot", "UTF-8")
            cfg.exportQuery(writer, query, r.toMap());
        }
    }


    @Test
    fun `imported anon graph query split node`() {
        val cfg = AnonGraphReduced();
        val query = SplitCollectionIndexedPairDotQuery();
        val results = query.match(cfg);
        val strwriter = StringWriter()
        query.exportQuery(strwriter)
        println(strwriter)
        val path = "/home/dor/Desktop/PG/dots/splitnode/"
        results.forEachIndexed { i, r ->
            val writer = PrintWriter("$path$i.dot", "UTF-8")
            cfg.exportQuery(writer, query, r.toMap());

        }
    }
    @Test
    fun `imported anon graph query combined with ports`(){
        val path = "/home/dor/Desktop/PG/dots/matchquries/"
        val cfg = AnonGraphReduced();
        val unionQuery = UnionUsingArrayDotQuery()
        val splitNodeQuery = SplitCollectionIndexedPairDotQuery()
        val loopQuery = LoopWithIteratorWithExtraStepsArrayDotQuery()
        val unionPort_value = unionQuery.ports().find { p -> p.captureGroup().get().contains("value") }
        val splitPort_input = splitNodeQuery.ports().find { p -> p.captureGroup().get().contains("input") }
        val loopPort_iterator = loopQuery.ports().find { p -> p.captureGroup().get().contains("iterator") }

        val unionResults = unionQuery.matchPorts(cfg)
        val splitResults = splitNodeQuery.matchPorts(cfg)
        val loopResults = loopQuery.matchPorts(cfg)
        var i = 0

        splitResults.forEach { (splitPorts, splitMatch) ->
            val splitM = splitMatch[splitPort_input]?.get(0)
            unionResults.forEach { (unionPorts, unionMatch) ->
                val unionM = unionMatch[unionPort_value]?.get(0)
                loopResults.forEach { (loopPorts, loopMatch) ->
                    val loopM = loopMatch[loopPort_iterator]?.get(0)
                    if (unionM == loopM && splitM == loopM) {
                        val combinedMap = unionMatch.toMutableMap()
                        combinedMap.clear()
                        combinedMap.putAll(unionMatch)
                        combinedMap.putAll(loopMatch)
                        combinedMap.putAll(splitMatch)
                        val writer = PrintWriter("$path$i.dot", "UTF-8")
                        i += 1
                        cfg.exportQuery(writer, null, combinedMap);
                    }
                }
            }
        }
    }

    @Test
    fun `imported anon graph query invoke with param`() {
        val cfg = AnonGraphReduced();
        val query = FunctionInvokeOneParamQueryDot();
        val results = query.match(cfg);
        val strwriter = StringWriter()
        query.exportQuery(strwriter)
        println(strwriter)
        val path = "/home/dor/Desktop/PG/dots/invokesWithParam/"
        results.forEachIndexed { i, r ->
            val writer = PrintWriter("$path$i.dot", "UTF-8")
            cfg.exportQuery(writer, query, r.toMap());
        }
    }

    @Test
    fun `imported anon graph query if with cond`() {
        val cfg = AnonGraphReduced();
        val query = IfWithConditionQueryDot();
        val results = query.match(cfg);
        val strwriter = StringWriter()
        query.exportQuery(strwriter)
        println(strwriter)
        val path = "/home/dor/Desktop/PG/dots/ifCond/"
        results.forEachIndexed { i, r ->
            val writer = PrintWriter("$path$i.dot", "UTF-8")
            cfg.exportQuery(writer, query, r.toMap());
        }
    }

    @Test
    fun `imported anon graph query func invoke two param in branch`() {
        val cfg = AnonGraphReduced();
        val query = FunctionInvokeTwoParamInsideScopeQueryDot();
        val results = query.match(cfg);
        val strwriter = StringWriter()
        query.exportQuery(strwriter)
        println(strwriter)
        val path = "/home/dor/Desktop/PG/dots/invokeTwoParamScope/"
        results.forEachIndexed { i, r ->
            val writer = PrintWriter("$path$i.dot", "UTF-8")
            cfg.exportQuery(writer, query, r.toMap());
        }
    }

    @Test
    fun `imported anon graph query func invoke two param`() {
        val cfg = AnonGraphReduced();
        val query = FunctionInvokeTwoParamInsideScopeQueryDot();
        val results = query.match(cfg);
        val strwriter = StringWriter()
        query.exportQuery(strwriter)
        println(strwriter)
        val path = "/home/dor/Desktop/PG/dots/invokeTwoParam/"
        results.forEachIndexed { i, r ->
            val writer = PrintWriter("$path$i.dot", "UTF-8")
            cfg.exportQuery(writer, query, r.toMap());
        }
    }

    @Test
    fun `uber match anon algo 3 queries - loop split union`() {
        val cfg = AnonGraphReduced();
        val unionQuery = UnionUsingArrayDotQuery()
        val splitNodeQuery = SplitCollectionIndexedPairDotQuery()
        val loopQuery = LoopWithIteratorWithExtraStepsArrayDotQuery()
        val unionPort_value = unionQuery.ports().find { p -> p.captureGroup().get().contains("value") }
        val splitPort_input = splitNodeQuery.ports().find { p -> p.captureGroup().get().contains("input") }
        val loopPort_iterator = loopQuery.ports().find { p -> p.captureGroup().get().contains("iterator") }

        val p1 = Pair(unionPort_value, loopPort_iterator)
        val p2 = Pair(splitPort_input, loopPort_iterator)

        var queries  = listOf(
            unionQuery,splitNodeQuery,loopQuery
        );

        val matches = uberMatch(cfg, listOf(unionQuery,splitNodeQuery,loopQuery), listOf(p1,p2));
        val path = "/home/dor/Desktop/PG/dots/uberMatchQueries1/"
        matches.forEachIndexed { i, m ->
            val writer = PrintWriter("$path$i.dot", "UTF-8")
            cfg.annotateGraphWithQuery(queries.zip(m.second).toList())
            cfg.exportQuery(writer, null, m.first);

        }
    }

    @Test
    fun `uber match anon algo 5 queries - loop split union func func`() {
        val cfg = AnonGraphReduced();
        val unionQuery = UnionUsingArrayDotQuery()
        val splitNodeQuery = SplitCollectionIndexedPairDotQuery()
        val loopQuery = LoopWithIteratorWithExtraStepsArrayDotQuery()
        val invoke1Query = FunctionInvokeOneParamQueryDot()
        val invoke2Query = FunctionInvokeOneParamQueryDot()
        val unionPort_value = unionQuery.ports().find { p -> p.captureGroup().get().contains("value") }
        val splitPort_input = splitNodeQuery.ports().find { p -> p.captureGroup().get().contains("input") }
        val splitPort_output1 = splitNodeQuery.ports().find { p -> p.captureGroup().get().contains("splitOutput1") }
        val splitPort_output2 = splitNodeQuery.ports().find { p -> p.captureGroup().get().contains("splitOutput2") }
        val loopPort_iterator = loopQuery.ports().find { p -> p.captureGroup().get().contains("iterator") }
        val invoke1_param = invoke1Query.ports().find { p -> p.captureGroup().get().contains("invokeInput") }
        val invoke2_param = invoke2Query.ports().find { p -> p.captureGroup().get().contains("invokeInput") }

        val p1 = Pair(unionPort_value, loopPort_iterator)
        val p2 = Pair(splitPort_input, loopPort_iterator)
        val p3 = Pair(splitPort_output1, invoke1_param)
        val p4 = Pair(splitPort_output2, invoke2_param)
        val matches = uberMatch(cfg, listOf(unionQuery,splitNodeQuery,loopQuery,invoke1Query,invoke2Query), listOf(p1,p2,p3,p4));
        val path = "/home/dor/Desktop/PG/dots/uberMatchQueries2/"
        matches.forEachIndexed { i, m ->
            val writer = PrintWriter("$path$i.dot", "UTF-8")
            cfg.exportQuery(writer, null, m.first);

        }
    }

    @Test
    fun `uber match anon algo 6 queries - loop split union func func if `() {
        val cfg = AnonGraphReduced();
        val unionQuery = UnionUsingArrayDotQuery()
        val splitNodeQuery = SplitCollectionIndexedPairDotQuery()
        val loopQuery = LoopWithIteratorWithExtraStepsArrayDotQuery()
        val invoke1Query = FunctionInvokeOneParamQueryDot()
        val invoke2Query = FunctionInvokeOneParamQueryDot()
        val ifQuery = IfWithConditionQueryDot()
        val unionPort_value = unionQuery.ports().find { p -> p.captureGroup().get().contains("value") }
        val splitPort_input = splitNodeQuery.ports().find { p -> p.captureGroup().get().contains("input") }
        val splitPort_output1 = splitNodeQuery.ports().find { p -> p.captureGroup().get().contains("splitOutput1") }
        val splitPort_output2 = splitNodeQuery.ports().find { p -> p.captureGroup().get().contains("splitOutput2") }
        val loopPort_iterator = loopQuery.ports().find { p -> p.captureGroup().get().contains("iterator") }
        val invoke1_param = invoke1Query.ports().find { p -> p.captureGroup().get().contains("invokeInput") }
        val invoke2_param = invoke2Query.ports().find { p -> p.captureGroup().get().contains("invokeInput") }
        val invoke1_output = invoke1Query.ports().find { p -> p.captureGroup().get().contains("invokeOutput") }
        val invoke2_output = invoke2Query.ports().find { p -> p.captureGroup().get().contains("invokeOutput") }
        val ifInput1_param = ifQuery.ports().find { p -> p.captureGroup().get().contains("ifInput1") }
        val ifInput2_param = ifQuery.ports().find { p -> p.captureGroup().get().contains("ifInput2") }

        val pairs = listOf(
            Pair(unionPort_value, loopPort_iterator),
            Pair(splitPort_input, loopPort_iterator),
            Pair(splitPort_output1, invoke1_param),
            Pair(splitPort_output2, invoke2_param),
            Pair(ifInput1_param, invoke1_output),
            Pair(ifInput2_param, invoke2_output),
        );
        var queries = listOf(unionQuery,splitNodeQuery,loopQuery,invoke1Query,invoke2Query,ifQuery);
        val matches = uberMatch(cfg, queries , pairs);
        val path = "/home/dor/Desktop/PG/dots/uberMatchQueries3/"
        matches.forEachIndexed { i, m ->
            val writer = PrintWriter("$path$i.dot", "UTF-8")
            cfg.annotateGraphWithQuery(queries.zip(m.second).toList())
            cfg.exportQuery(writer, null, m.first);
            //cfg.exportQuerySubgraph(writer, queries.zip(m.second).toList());

        }
    }


    @Test
    fun `uber match anon algo 7 queries - loop split union func func if invoke2`() {
        val cfg = AnonGraphReduced();
        val unionQuery = UnionUsingArrayDotQuery()
        val splitNodeQuery = SplitCollectionIndexedPairDotQuery()
        val loopQuery = LoopWithIteratorWithExtraStepsArrayDotQuery()
        val invoke1Query = FunctionInvokeOneParamQueryDot()
        val invoke2Query = FunctionInvokeOneParamQueryDot()
        val invokeScopeQuery = FunctionInvokeTwoParamInsideScopeQueryDot()
//        val invokeScopeQuery = FunctionInvokeTwoParamInsideQueryDot()
        val ifQuery = IfWithConditionQueryDot()
        val unionPort_value = unionQuery.ports().find { p -> p.captureGroup().get().contains("value") }
        val splitPort_input = splitNodeQuery.ports().find { p -> p.captureGroup().get().contains("input") }
        val splitPort_output1 = splitNodeQuery.ports().find { p -> p.captureGroup().get().contains("splitOutput1") }
        val splitPort_output2 = splitNodeQuery.ports().find { p -> p.captureGroup().get().contains("splitOutput2") }
        val loopPort_iterator = loopQuery.ports().find { p -> p.captureGroup().get().contains("iterator") }
        val invoke1_param = invoke1Query.ports().find { p -> p.captureGroup().get().contains("invokeInput") }
        val invoke2_param = invoke2Query.ports().find { p -> p.captureGroup().get().contains("invokeInput") }
        val invoke1_output = invoke1Query.ports().find { p -> p.captureGroup().get().contains("invokeOutput") }
        val invoke2_output = invoke2Query.ports().find { p -> p.captureGroup().get().contains("invokeOutput") }
        val ifInput1_param = ifQuery.ports().find { p -> p.captureGroup().get().contains("ifInput1") }
        val ifInput2_param = ifQuery.ports().find { p -> p.captureGroup().get().contains("ifInput2") }
        val if_false = ifQuery.ports().find { p -> p.captureGroup().get().contains("falseSuccessor") }
        val invokeScopeQuery_scope = ifQuery.ports().find { p -> p.captureGroup().get().contains("scopeBranch") }
        val invokeScopeQuery_input1 = ifQuery.ports().find { p -> p.captureGroup().get().contains("invokeScopeInput1") }
//        val invokeScopeQuery_input1 = ifQuery.ports().find { p -> p.captureGroup().get().contains("invoke2Input1") }
        val invokeScopeQuery_input2 = ifQuery.ports().find { p -> p.captureGroup().get().contains("invokeScopeInput1") }
//        val invokeScopeQuery_input2 = ifQuery.ports().find { p -> p.captureGroup().get().contains("invoke2Input2") }

        val pairs = listOf(
            Pair(unionPort_value, loopPort_iterator),
            Pair(splitPort_input, loopPort_iterator),
            Pair(splitPort_output1, invoke1_param),
            Pair(splitPort_output2, invoke2_param),
            Pair(ifInput1_param, invoke1_output),
            Pair(ifInput2_param, invoke2_output),
            Pair(if_false, invokeScopeQuery_scope),
            Pair(invokeScopeQuery_input1, splitPort_output1),
            Pair(invokeScopeQuery_input2, splitPort_output2),
        );

        val queries = listOf(
            unionQuery,
            splitNodeQuery,
            loopQuery,
            invoke1Query,
            invoke2Query,
            ifQuery,
            invokeScopeQuery
        );
        //val path = "/home/dor/Desktop/PG/dots/queries/"
        //var j = 0;

//        queries.forEach {
//            val writer = PrintWriter("$path$j.dot", "UTF-8")
//            it.exportQuery(writer)
//            j++
//        }
        val matches = uberMatch(cfg, queries , pairs);
        val path = "/home/dor/Desktop/PG/dots/uberMatchQueries4/"
        matches.forEachIndexed { i, m ->
            val writer = PrintWriter("$path$i.dot", "UTF-8")
            println("##############   " + i + "   ################")
            m.second[6].forEach {
                //print(it.key.toString() + " = ")
                println(it.value[0])
            }
            cfg.exportQuerySubgraph(writer, queries.zip(m.second).toList());


        }
    }

}