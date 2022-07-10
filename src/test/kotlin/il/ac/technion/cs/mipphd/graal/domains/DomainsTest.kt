//package il.ac.technion.cs.mipphd.graal.domains
//
//import il.ac.technion.cs.mipphd.graal.ForwardsAnalysis
//import il.ac.technion.cs.mipphd.graal.MethodToGraph
//import il.ac.technion.cs.mipphd.graal.graphquery.GraphQueryVertexM
//import il.ac.technion.cs.mipphd.graal.utils.*
//import org.junit.jupiter.api.Assertions.*
//import org.junit.jupiter.api.Disabled
//import org.junit.jupiter.api.DisplayName
//import org.junit.jupiter.api.Nested
//import org.junit.jupiter.api.Test
//import java.io.StringWriter
//import kotlin.reflect.jvm.javaMethod
//
//
//internal class DomainsTest {
//
//
//    @Nested
//    @DisplayName("Prerequisites")
//    inner class PrerequisiteTests {
//        private val mccarthy91Method = ::mccarthy91.javaMethod
//        private val methodToGraph = MethodToGraph()
//
//        @Test
//        fun `display mccarthy 91 function`() {
//            val cfg = methodToGraph.getCFG(mccarthy91Method)
//            val adapted = GraalAdapter.fromGraal(cfg)
//
//            val sw = StringWriter()
//            adapted.exportQuery(sw, null,null)
//
//            println(sw.buffer)
//            assertTrue(true)
//        }
//
//        @Test
//        fun `analyze mccarthy 91 function via graph executor`() {
//            val cfg = methodToGraph.getCFG(mccarthy91Method)
//            val graph = GraalAdapter.fromGraal(cfg)
//            val executor = McCarthy91Analysis(graph)
//
//            val results = executor.iterateUntilFixedPoint()
//
//            for ((_, item) in results) {
//                if (item.statements.isNotEmpty())
//                    println(item.statements)
//            }
//        }
//
//        @Disabled
//        @Test
//        fun `some analysis`() {
//            val cfg = methodToGraph.getCFG(mccarthy91Method)
//            val adapted = GraalAdapter.fromGraal(cfg)
//            val analysis = object : ForwardsAnalysis<MutableSet<Int>>(
//                adapted,
//                adapted.vertexSet().toList(),
//                adapted.vertexSet().filter { GraphQueryVertexM.fromQuery("is('StartNode')").match(it) },
//                adapted.vertexSet().filter { GraphQueryVertexM.fromQuery("is('ReturnNode')").match(it) }) {
//                override fun newInitial(): MutableSet<Int> = mutableSetOf()
//
//                override fun copy(source: MutableSet<Int>, dest: MutableSet<Int>) {
//                    dest.clear()
//                    dest.addAll(source)
//                }
//
//                override fun flow(input: MutableSet<Int>, d: NodeWrapper, out: MutableSet<Int>) {
//                    TODO("Not yet implemented")
//                }
//
//                override fun merge(in1: MutableSet<Int>, in2: MutableSet<Int>, out: MutableSet<Int>) {
//                    out.clear()
//                    out.addAll(in1)
//                    out.addAll(in2)
//                }
//
//            }
//            analysis.doAnalysis()
//        }
//    }
//
//
//}