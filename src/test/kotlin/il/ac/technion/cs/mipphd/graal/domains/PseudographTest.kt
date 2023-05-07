package il.ac.technion.cs.mipphd.graal.domains

import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Parser
import il.ac.technion.cs.mipphd.graal.domains.psuedograph.Kruskal1
import il.ac.technion.cs.mipphd.graal.domains.psuedograph.MatchPoint
import il.ac.technion.cs.mipphd.graal.graphquery.AnalysisNode
import il.ac.technion.cs.mipphd.graal.graphquery.GraphQuery
import il.ac.technion.cs.mipphd.graal.graphquery.GraphQueryVertex
import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.reflect.jvm.javaMethod

typealias Matches = MutableList<MutableMap<GraphQueryVertex, MutableList<AnalysisNode>>>

val UnionQuery = GraphQuery.importQuery(
    """digraph G {
  n1747810414 [ label="is('java.StoreIndexedNode')" ];
  n1722105026 [ label="is('calc.AddNode')" ];
  n1699268208 [ label="(?P<array>)|is('ValueNode')" ];
  n1309175485 [ label="(?P<index>)|is('ValueNode')" ];
  n1147572458 [ label="(?P<value>)|is('ValueNode')" ];
  n1699268208 -> n1747810414 [ label="(is('DATA')) and ((name()) = ('array'))" ];
  n1147572458 -> n1747810414 [ label="(is('DATA')) and ((name()) = ('value'))" ];
  n1309175485 -> n1747810414 [ label="(is('DATA')) and ((name()) = ('index'))" ];
  n1309175485 -> n1722105026 [ label="is('DATA')" ];
  n1722105026 -> n1309175485 [ label="is('DATA')" ];
}"""
)

val LoopWithIteratorQuery = GraphQuery.importQuery(
    """digraph G {
  v1 [ label="is('LoopBeginNode')" ];
  v2 [ label="not (is('IfNode'))" ];
  v3 [ label="is('IfNode')" ];
  v4 [ label="is('ValuePhiNode')" ];
  v5 [ label="is('calc.AddNode')" ];
  v6 [ label="is('calc.CompareNode')" ];
  v7 [ label="is('ValueNode')" ];
  v8 [ label="(?P<iterator>)|is('java.LoadIndexedNode')"]
  v9 [ label="(?P<source>)|is('ValueNode')" ];
  v1 -> v2 [ label="*|is('CONTROL')" ];
  v2 -> v3 [ label="is('CONTROL')" ];
  v1 -> v4 [ label="(name()) = ('merge')" ];
  v5 -> v4 [ label="is('DATA')" ];
  v4 -> v5 [ label="is('DATA')" ];
  v4 -> v6 [ label="is('DATA')" ];
  v7 -> v4 [ label="not ((name()) = ('merge'))" ];
  v4 -> v8 [ label="name() ='index'" ]
  v9 -> v8 [ label="name() ='array'" ]
}"""
)

val SplitQuery = GraphQuery.importQuery(
    """digraph G {
  v1 [ label="(?P<input>)|is('ValueNode')" ];
  v3 [ label="(is('java.LoadFieldNode')) or (is('java.LoadIndexedNode'))" ];
  v4 [ label="(?P<splitOutput1>)|(is('java.LoadFieldNode')) or (is('java.LoadIndexedNode'))" ];
  v5 [ label="(is('java.LoadFieldNode')) or (is('java.LoadIndexedNode'))" ];
  v6 [ label="(?P<splitOutput2>)|(is('java.LoadFieldNode')) or (is('java.LoadIndexedNode'))" ];
  v7 [ label="not((is('java.LoadFieldNode')) or (is('java.LoadIndexedNode')))"];
  v8 [ label="not((is('java.LoadFieldNode')) or (is('java.LoadIndexedNode')))"];
  v1 -> v3 [ label="*|is('DATA')" ];
  v1 -> v5 [ label="*|is('DATA')" ];
  v3 -> v4 [ label="is('DATA')" ];
  v5 -> v6 [ label="is('DATA')" ];
  v6 -> v7 [ label="is('DATA')" ];
  v4 -> v8 [ label="is('DATA')" ];
}
"""
)

val FunctionInvoke1Param = GraphQuery.importQuery(
    """
digraph G {
  n1973339225 [ label="(?P<invokeOutput>)|(is('InvokeWithExceptionNode') or is('InvokeNode'))" ];
  n1149290751 [ label="is('java.MethodCallTargetNode')" ];
  n276649967 [ label="(?P<invokeInput>)|is('ValueNode')" ];
  n1707443639 [ label="is('ValueNode')" ];
  n1149290751 -> n1973339225 [ label="is('DATA')" ];
  n276649967 -> n1149290751 [ label="is('DATA')" ];
  n1973339225 -> n1707443639 [ label="is('DATA')" ];
}"""
)

val IfQuery = GraphQuery.importQuery(
    """digraph G {
  n1581722373 [ label="is('IfNode')" ];
  n401012039 [ label="is('calc.CompareNode')" ];
  n551661064 [ label="(?P<ifInput1>)|is('ValueNode')" ];
  n2060862812 [ label="(?P<ifInput2>)|is('ValueNode')" ];
  n497964544 [ label="(?P<trueSuccessor>)|is('BeginNode')" ];
  n1036178495 [ label="(?P<falseSuccessor>)|is('BeginNode')" ];
  n401012039 -> n1581722373 [ label="is('DATA')" ];
  n1581722373 -> n497964544 [ label="is('CONTROL') and (name() = 'trueSuccessor')" ];
  n1581722373 -> n1036178495 [ label="is('CONTROL') and (name() = 'falseSuccessor')" ];
  n551661064 -> n401012039 [ label="is('DATA') and (name() = 'x')" ];
  n2060862812 -> n401012039 [ label="is('DATA') and (name() = 'y')" ];
}
"""
)

val FunctionInvoke2Param = GraphQuery.importQuery(
    """digraph G {
  begin [ label="(?P<scopeBranch>)|is('BeginNode')" ];
  valueNode [ label="is('ValueNode') and not is('EndNode')" ];
  invokeScopeOutput [ label="(?P<invokeScopeOutput>)|(is('InvokeWithExceptionNode')) or (is('InvokeNode'))" ];
  MethodCallTargetNode [ label="is('java.MethodCallTargetNode')" ];
  invokeScopeInput1 [ label="(?P<invokeScopeInput1>)|is('ValueNode')" ];
  invokeScopeInput2 [ label="(?P<invokeScopeInput2>)|is('ValueNode')" ];
  MethodCallTargetNode -> invokeScopeOutput [ label="is('DATA')" ];
  invokeScopeInput1 -> MethodCallTargetNode [ label="is('DATA')" ];
  invokeScopeInput2 -> MethodCallTargetNode [ label="is('DATA')" ];
  begin -> valueNode [ label="*|is('CONTROL')" ];
  valueNode -> invokeScopeOutput [ label="is('CONTROL')" ];
}"""
)

@Disabled
internal class PseudographTest {
    val methodToGraph = MethodToGraph(false) // this works much better with optimizations turned OFF
    val klaxon = Klaxon()
    val outputDir: String = File("./build/outputs/").canonicalPath

    @Nested
    @DisplayName("Puzzle queries")
    inner class PuzzleQueries {
        @Test
        fun `display Union query`() {
            println(UnionQuery.export())
        }

        @Test
        fun `display Loop with Iterator query`() {
            println(LoopWithIteratorQuery.export())
        }

        @Test
        fun `display Split query`() {
            println(SplitQuery.export())
        }

        @Test
        fun `display Function Invoke 1 Param query`() {
            println(FunctionInvoke1Param.export())
        }

        @Test
        fun `display If query`() {
            println(IfQuery.export())
        }

        @Test
        fun `display Function Invoke 2 Param query`() {
            println(FunctionInvoke2Param.export())
        }
    }

    @Nested
    @DisplayName("Match results on Kruskal1")
    inner class Kruskal1MatchResults {
        private val kruskalOutputDir: Path = Path(outputDir).resolve(Path("kruskal1"))
        private val graph = methodToGraph.getAnalysisGraph(Kruskal1::KruskalMST.javaMethod!!)
        private fun match(query: GraphQuery): Matches = query.match(graph)
        private fun Matches.toJSON(): String {
            val encoded = map { match ->
                match.flatMap { (queryVertex, irVertices) ->
                    irVertices.map { node ->
                        MatchPoint(
                            queryVertexName = queryVertex.name,
                            query = queryVertex.label(),
                            irVertexId = if (node is AnalysisNode.IR) node.index else null,
                            vertexName = node.nodeName
                        )
                    }
                }
            }
            val uglyJson = klaxon.toJsonString(encoded)
            return (Parser.default().parse(StringBuilder(uglyJson)) as JsonArray<*>).toJsonString(true)
        }

        init {
            kruskalOutputDir.toFile().mkdirs()
        }

        @Test
        fun `Union`() {
            val res = match(UnionQuery).toJSON()
            writeFile(kruskalOutputDir.resolve("union.json"), res)
        }

        @Test
        fun `Loop with Iterator`() {
            val res = match(LoopWithIteratorQuery).toJSON()

            writeFile(kruskalOutputDir.resolve("loopWithIterator.json"), res)
        }

        @Test
        fun `Split`() {
            val res = match(SplitQuery).toJSON()

            writeFile(kruskalOutputDir.resolve("split.json"), res)
        }

        @Test
        fun `Function Invoke 1 Param`() {
            val res = match(FunctionInvoke1Param).toJSON()

            writeFile(kruskalOutputDir.resolve("functionInvoke1Param.json"), res)
        }

        @Test
        fun `If`() {
            val res = match(IfQuery).toJSON()

            writeFile(kruskalOutputDir.resolve("if.json"), res)
        }

        @Test
        fun `Function Invoke 2 Param`() {
            val res = match(FunctionInvoke2Param).toJSON()

            writeFile(kruskalOutputDir.resolve("functionInvoke2Param.json"), res)
        }
    }

    @Nested
    @DisplayName("Code graphs")
    inner class CodeGraphs {
        private val codeGraphOutputDir: Path = Path(outputDir).resolve(Path("codeGraph"))
            .apply { toFile().mkdirs() }

        @Test
        fun `create Kruskal1 - KruskalMST code graph`() {
            val graph = methodToGraph.getAnalysisGraph(Kruskal1::KruskalMST.javaMethod)
            graph.simplify()

            writeFile(codeGraphOutputDir.resolve("kruskal1.dot"), graph.export())
        }
    }

    private fun writeFile(fileName: Path, contents: String) {
        fileName.toFile().printWriter().use { w ->
            w.write(contents)
            w.close()
        }
    }
}