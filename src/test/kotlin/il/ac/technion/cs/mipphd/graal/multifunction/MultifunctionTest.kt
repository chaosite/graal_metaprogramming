package il.ac.technion.cs.mipphd.graal.multifunction

import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import kotlin.reflect.jvm.javaMethod

fun a(x: String) = 5 + b(x)
fun b(x: String) = x.toLong() * 2
fun c(x: String) = a(x) + b(x)

internal class MultifunctionTest {
    private val methodToGraph = MethodToGraph()
}