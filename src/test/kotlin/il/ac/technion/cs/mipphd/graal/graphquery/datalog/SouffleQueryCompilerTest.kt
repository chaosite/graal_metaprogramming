package il.ac.technion.cs.mipphd.graal.graphquery.datalog

import il.ac.technion.cs.mipphd.graal.domains.mccarthy91
import il.ac.technion.cs.mipphd.graal.graphquery.GraphQuery
import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createTempDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile
import kotlin.reflect.jvm.javaMethod

val trivialQuery = GraphQuery.importQuery("""
digraph G {
    n [ label = "(?P<start>)|is('StartNode')" ];
}
""".trimIndent())

val simpleQuery = GraphQuery.importQuery("""
digraph G {
	arith [ label="(?P<arithmeticNode>)|" ];
	x [ label="(?P<x>)|" ];
	y [ label="(?P<y>)|" ];

	x -> arith [ label="is('DATA') and name() = 'x'" ];
	y -> arith [ label="is('DATA') and name() = 'y'" ];
}
""".trimIndent())

val kleeneQuery = GraphQuery.importQuery("""
digraph G {
    ifnode [ label="(?P<ifpathnode>)|is('IfNode')" ];
    truepath [ label="(?P<truepath>)|" ];
    truepath2 [ label="(?P<truepath2>)|" ];
    
    falsepath [ label="(?P<falsepath>)|" ];
    falsepath2 [ label="(?P<falsepath2>)|" ];
    
    merge [ label="(?P<merge>)|" ];
    
    ifnode -> truepath [ label="is('CONTROL') and name() = 'trueSuccessor'" ];
    ifnode -> falsepath [ label="is('CONTROL') and name() = 'falseSuccessor'" ];
    
    truepath -> truepath2 [ label="*|is('CONTROL')" ];
    falsepath -> falsepath2 [ label = "*|is('CONTROL')" ];
    
    truepath2 -> merge [ label = "is('CONTROL')" ];
    falsepath2 -> merge [ label = "is('CONTROL')" ];
}
""".trimIndent())

val sunQuery = GraphQuery.importQuery("""
digraph G {
    sources [ label="[](?P<sources>)|" ];
    destination [ label="(?P<destination>)|" ];
    
    sources -> destination [ label = "is('CONTROL')" ];
}
""".trimIndent())

class SouffleQueryCompilerTest {
    private lateinit var compiler: SouffleQueryCompiler
    private lateinit var workDir: Path
    @BeforeEach
    fun setUp() {
        workDir = createTempDirectory()
        compiler = SouffleQueryCompiler(workDir, Paths.get("/home/mip/Bench/souffle/target/bin/souffle"))
    }

    @AfterEach
    fun tearDown() {
        workDir.toFile().deleteRecursively()
    }

    @Nested
    @DisplayName("Integration")
    inner class IntegrationTests {
        val methodToGraph = MethodToGraph()
        @Nested
        @DisplayName("Compilation")
        inner class CompilationTests {


            @Test
            fun `compile trivial query and verify outputs exist`() {
                compiler.compile(listOf(trivialQuery))

                assertTrue(workDir.resolve("script.dl").isRegularFile())
                assertTrue(workDir.resolve("script.cpp").isRegularFile())
                assertTrue(workDir.resolve("script").isExecutable())
            }

            @Test
            fun `compile simple query and verify outputs exist`() {
                compiler.compile(listOf(simpleQuery))

                assertTrue(workDir.resolve("script.dl").isRegularFile())
                assertTrue(workDir.resolve("script.cpp").isRegularFile())
                assertTrue(workDir.resolve("script").isExecutable())
            }

            @Test
            fun `compile Kleene query and verify outputs exist`() {
                compiler.compile(listOf(kleeneQuery))

                assertTrue(workDir.resolve("script.dl").isRegularFile())
                assertTrue(workDir.resolve("script.cpp").isRegularFile())
                assertTrue(workDir.resolve("script").isExecutable())
            }

            @Test
            fun `compile sun query and verify outputs exist`() {
                compiler.compile(listOf(sunQuery))

                assertTrue(workDir.resolve("script.dl").isRegularFile())
                assertTrue(workDir.resolve("script.cpp").isRegularFile())
                assertTrue(workDir.resolve("script").isExecutable())
            }

            @Test
            fun `compile multiple queries and verify outputs exist`() {
                compiler.compile(listOf(trivialQuery, simpleQuery, kleeneQuery, sunQuery))

                assertTrue(workDir.resolve("script.dl").isRegularFile())
                assertTrue(workDir.resolve("script.cpp").isRegularFile())
                assertTrue(workDir.resolve("script").isExecutable())
            }
        }

        @Nested
        @DisplayName("Execution")
        inner class ExecutionTests {
            private val graph = methodToGraph.getAnalysisGraph(::mccarthy91.javaMethod)

            @Test
            fun `execute trivial query`() {
                val query = compiler.compile(listOf(trivialQuery))

                val results = query.execute(graph)

                assertEquals(results.size, 1)
                assertTrue(results.containsKey(trivialQuery))
                assertEquals(results[trivialQuery]?.size, 1)
                assertEquals(results[trivialQuery]?.first()?.size, 1)
                assertEquals(results[trivialQuery]?.first()?.values?.first()?.size, 1)
                assertEquals(results[trivialQuery]?.first()?.values?.first()?.first()?.isType("StartNode"), true)
            }

            @Test
            fun `execute simple query`() {
                val query = compiler.compile(listOf(simpleQuery))

                val results = query.execute(graph)

                assertEquals(results.size, 1)
                assertTrue(results.containsKey(simpleQuery))

                val x = simpleQuery.vertexSet().find { it.name == "x" }!!
                val y = simpleQuery.vertexSet().find { it.name == "y" }!!
                val arith = simpleQuery.vertexSet().find { it.name == "arith" }!!

                for (map in results[simpleQuery]!!) {
                    assertEquals(map[x]?.size, 1)
                    assertEquals(map[y]?.size, 1)
                    assertEquals(map[arith]?.size, 1)

                    map[x]?.forEach { assertTrue(x.match(it)) }
                    map[y]?.forEach { assertTrue(y.match(it)) }
                    map[arith]?.forEach { assertTrue(arith.match(it)) }
                }
            }
        }
    }
}