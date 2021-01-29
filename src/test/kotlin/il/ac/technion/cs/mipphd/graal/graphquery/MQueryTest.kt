package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.Listable
import il.ac.technion.cs.mipphd.graal.MethodToGraph
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapperUtils
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import org.amshove.kluent.*
import org.graalvm.compiler.nodes.Invoke
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.reflect.jvm.javaMethod

@ExtendWith(MockKExtension::class)
internal class MQueryTest {
    @Nested
    @DisplayName("AST generation tests")
    inner class ASTTest {
        @Test
        fun `equals parses to ast`() {
            val res = parseMQuery("1 = 2")

            res shouldBeInstanceOf Equals::class
            val equals = res as Equals
            equals.lvalue shouldBeInstanceOf IntegerValue::class
            equals.rvalue shouldBeInstanceOf IntegerValue::class
            (equals.lvalue as IntegerValue).value shouldBe 1
            (equals.rvalue as IntegerValue).value shouldBe 2
        }

        @Test
        fun `and clause parses to ast`() {
            val res = parseMQuery("1 = 1 and 2 = 3")

            res shouldBeInstanceOf And::class
            val and = res as And
            and.left shouldBeInstanceOf Equals::class
            and.right shouldBeInstanceOf Equals::class
            val rightEquals = and.right as Equals
            (rightEquals.lvalue as IntegerValue).value shouldBe 2
            (rightEquals.rvalue as IntegerValue).value shouldBe 3
        }

        @Test
        fun `or clause has higher priority than and (left) and parses to ast`() {
            val res = parseMQuery("1 = 1 and 2 = 3 or five = 5")

            res shouldBeInstanceOf Or::class
            val or = res as Or
            or.left shouldBeInstanceOf And::class
            val and = or.left as And
            and.left shouldBeInstanceOf Equals::class
            and.right shouldBeInstanceOf Equals::class
            val rightEquals = and.right as Equals
            (rightEquals.lvalue as IntegerValue).value shouldBe 2
            (rightEquals.rvalue as IntegerValue).value shouldBe 3
        }

        @Test
        fun `or clause has higher priority than and (right) and parses to ast`() {
            val res = parseMQuery("five = 5 or 1 = 1 and 2 = 3")

            res shouldBeInstanceOf Or::class
            val or = res as Or
            or.right shouldBeInstanceOf And::class
            val and = or.right as And
            and.left shouldBeInstanceOf Equals::class
            and.right shouldBeInstanceOf Equals::class
            val rightEquals = and.right as Equals
            (rightEquals.lvalue as IntegerValue).value shouldBe 2
            (rightEquals.rvalue as IntegerValue).value shouldBe 3
        }

        @Test
        fun `function and string parses to ast`() {
            val res = parseMQuery("is(2, 3) = \"bar\"")

            val equals = res as Equals
            equals.lvalue shouldBeInstanceOf FuncCall::class
            val func = res.lvalue as FuncCall
            equals.rvalue shouldBeInstanceOf StringValue::class
            val str = res.rvalue as StringValue
            func.func shouldBeEqualTo Variable("is")
            func.parameters shouldHaveSize 2
            func.parameters.filterIsInstance<IntegerValue>().map(IntegerValue::value) shouldContainAll listOf(2, 3)
            str.value shouldBeEqualTo "bar"
        }
    }
    @Nested
    @DisplayName("Query interpretation tests")
    inner class InterpretTest {
        @RelaxedMockK
        lateinit var mockNode: NodeWrapper

        @Test
        fun `true equals interprets as true for integer`() {
            parseMQuery("1 = 1").interpret(mockNode) shouldBe true
        }

        @Test
        fun `false equals interprets as false for integer`() {
            parseMQuery("2 = 1").interpret(mockNode) shouldBe false
        }

        @Test
        fun `true equals interprets as true for string`() {
            parseMQuery(""""foo bar" = "foo bar"""").interpret(mockNode) shouldBe true
        }

        @Test
        fun `false equals interprets as false for string`() {
            parseMQuery(""""foo bar" = "bar foo"""").interpret(mockNode) shouldBe false
        }

        @Test
        fun `type mismatched equals interprets as false`() {
            parseMQuery(""""foo bar" = 2""").interpret(mockNode) shouldBe false
        }

        @Test
        fun `true and clause interprets as true`() {
            parseMQuery("""2 = 2 and 3 = 3""").interpret(mockNode) shouldBe true
        }

        @Test
        fun `false and clause interprets as false`() {
            parseMQuery("""3 = 2 and 3 = 3""").interpret(mockNode) shouldBe false
        }

        @Test
        fun `false and clause interprets as false alt`() {
            parseMQuery("""2 = 2 and 2 = 3""").interpret(mockNode) shouldBe false
        }
    }

    @Nested
    @DisplayName("Predefined functions tests")
    inner class PredefinedFunctionsTest {
        private val methodToGraph = MethodToGraph()
        private val maximum = Listable::maximum.javaMethod

        @Test
        fun `is invoke on invoke node interprets as true`() {
            val maximumGraph = methodToGraph.getCFG(maximum)
            val invokeNode = NodeWrapper(maximumGraph.asCFG().graph.nodes.filterIsInstance<Invoke>()[0])

            parseMQuery("""is("Invoke")""").interpret(invokeNode) shouldBe true
        }

        @Test
        fun `is phinode on invoke node interprets as false`() {
            val maximumGraph = methodToGraph.getCFG(maximum)
            val invokeNode = NodeWrapper(maximumGraph.asCFG().graph.nodes.filterIsInstance<Invoke>()[0])

            parseMQuery("""is("PhiNode")""").interpret(invokeNode) shouldBe false
        }

        @Test
        fun `method access on invoke node compares to actual class and method names correctly`() {
            val maximumGraph = methodToGraph.getCFG(maximum)
            val invokeNode = NodeWrapper(maximumGraph.asCFG().graph.nodes.filterIsInstance<Invoke>()[0])

            val actualName = NodeWrapperUtils.getTargetMethod(invokeNode).name
            val actualClass = NodeWrapperUtils.getTargetMethod(invokeNode).declaringClassName

            val ast = parseMQuery(""" is("Invoke") and method().name = "$actualName" and method().className = "$actualClass" """).interpret(invokeNode) shouldBe true
        }
    }
}