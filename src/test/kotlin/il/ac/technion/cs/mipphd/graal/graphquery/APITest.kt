package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.domains.mccarthy91
import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter
import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod

val query = """
    digraph G {
    	arith [ label="(?P<arithmeticNode>)|1 = 1" ];
    	x [ label="(?P<x>)|1 = 1" ];
    	y [ label="(?P<y>)|1 = 1" ];

    	x -> arith [ label="is('DATA') and name() = 'x'" ];
    	y -> arith [ label="is('DATA') and name() = 'y'" ];
    }
""".trimIndent()

fun sample(x: Long, y: Long): Long {
    return x + y
}

internal class APITest {

    @Test
    fun compileAndQueryTest() {
        val results = compileAndQuery(MethodToGraph().getAdaptedCFG(::sample.javaMethod), query)

        println(results)
    }
}