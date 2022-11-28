package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.Listable
import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import il.ac.technion.cs.mipphd.graal.utils.WrappedIRNodeImpl
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapperUtils
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import org.amshove.kluent.*
import org.graalvm.compiler.graph.Node
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
        fun `metadata kleene test`() {
            val res = parseMQuery("*|1 = 1")
            res shouldBeInstanceOf Metadata::class
            res as Metadata
            res.options shouldContain MetadataOption.Kleene
        }

        @Test
        fun `metadata repeated test`() {
            val res = parseMQuery("[]|1 = 1")
            res shouldBeInstanceOf Metadata::class
            res as Metadata
            res.options shouldContain MetadataOption.Repeated
        }

        @Test
        fun `metadata capture test`() {
            val res = parseMQuery("(?P<foo>)|1 = 1")
            res shouldBeInstanceOf Metadata::class
            res as Metadata
            res.options shouldHaveSize 1
            val captureName = res.options[0]
            captureName shouldBeInstanceOf MetadataOption.CaptureName::class
            captureName as MetadataOption.CaptureName
            captureName.name shouldBeEqualTo "foo"
        }

        @Test
        fun `equals parses to ast`() {
            val res = parseMQuery("1 = 2")

            res shouldBeInstanceOf Metadata::class
            res as Metadata
            res.query shouldBeInstanceOf Equals::class
            val equals = res.query as Equals
            equals.lvalue shouldBeInstanceOf IntegerValue::class
            equals.rvalue shouldBeInstanceOf IntegerValue::class
            (equals.lvalue as IntegerValue).value shouldBe 1
            (equals.rvalue as IntegerValue).value shouldBe 2
        }

        @Test
        fun `and clause parses to ast`() {
            val res = parseMQuery("1 = 1 and 2 = 3")

            res as Metadata
            res.query shouldBeInstanceOf And::class
            val and = res.query as And
            and.left shouldBeInstanceOf Equals::class
            and.right shouldBeInstanceOf Equals::class
            val rightEquals = and.right as Equals
            (rightEquals.lvalue as IntegerValue).value shouldBe 2
            (rightEquals.rvalue as IntegerValue).value shouldBe 3
        }

        @Test
        fun `or clause has higher priority than and (left) and parses to ast`() {
            val res = parseMQuery("1 = 1 and 2 = 3 or five = 5")

            res as Metadata
            res.query shouldBeInstanceOf Or::class
            val or = res.query as Or
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

            res as Metadata
            res.query shouldBeInstanceOf Or::class
            val or = res.query as Or
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

            res as Metadata
            val equals = res.query as Equals
            equals.lvalue shouldBeInstanceOf FuncCall::class
            val func = equals.lvalue as FuncCall
            equals.rvalue shouldBeInstanceOf StringValue::class
            val str = equals.rvalue as StringValue
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
        lateinit var mockQueryTarget: QueryTarget

        @Test
        fun `true equals interprets as true for integer`() {
            parseMQuery("1 = 1").interpret(mockQueryTarget) shouldBe true
        }

        @Test
        fun `false equals interprets as false for integer`() {
            parseMQuery("2 = 1").interpret(mockQueryTarget) shouldBe false
        }

        @Test
        fun `true equals interprets as true for string`() {
            parseMQuery(""""foo bar" = "foo bar"""").interpret(mockQueryTarget) shouldBe true
        }

        @Test
        fun `false equals interprets as false for string`() {
            parseMQuery(""""foo bar" = "bar foo"""").interpret(mockQueryTarget) shouldBe false
        }

        @Test
        fun `type mismatched equals interprets as false`() {
            parseMQuery(""""foo bar" = 2""").interpret(mockQueryTarget) shouldBe false
        }

        @Test
        fun `true and clause interprets as true`() {
            parseMQuery("""2 = 2 and 3 = 3""").interpret(mockQueryTarget) shouldBe true
        }

        @Test
        fun `false and clause interprets as false`() {
            parseMQuery("""3 = 2 and 3 = 3""").interpret(mockQueryTarget) shouldBe false
        }

        @Test
        fun `false and clause interprets as false alt`() {
            parseMQuery("""2 = 2 and 2 = 3""").interpret(mockQueryTarget) shouldBe false
        }

        @Test
        fun `true or clause interprets as true on the left`() {
            parseMQuery("""2 = 2 or 1 = 3""").interpret(mockQueryTarget) shouldBe true
        }

        @Test
        fun `true or clause interprets as true on the right`() {
            parseMQuery("""1 = 2 or 3 = 3""").interpret(mockQueryTarget) shouldBe true
        }

        @Test
        fun `false or clause interprets as false`() {
            parseMQuery("""1 = 2 or 3 = 4""").interpret(mockQueryTarget) shouldBe false
        }
    }

    @Nested
    @DisplayName("Predefined functions tests")
    inner class PredefinedFunctionsTest {
        private val methodToGraph = MethodToGraph()
        private val maximum = Listable::maximum.javaMethod
        private val maximumGraph = methodToGraph.getCFG(maximum)
        private val invokeNode =
            AnalysisNode.IR(WrappedIRNodeImpl(maximumGraph.asCFG().graph.nodes.filterIsInstance<Invoke>()[0] as Node))
        private val invokeQueryTarget = QueryTargetNode(invokeNode)

        @Test
        fun `is invoke on invoke node interprets as true`() {
            parseMQuery("""is("Invoke")""").interpret(invokeQueryTarget) shouldBe true
        }

        @Test
        fun `is phinode on invoke node interprets as false`() {
            parseMQuery("""is("PhiNode")""").interpret(invokeQueryTarget) shouldBe false
        }

        @Test
        fun `method access on invoke node compares to actual class and method names correctly`() {
            val actualName = NodeWrapperUtils.getTargetMethod(invokeNode).name
            val actualClass = NodeWrapperUtils.getTargetMethod(invokeNode).declaringClassName

            parseMQuery(""" is("Invoke") and method().name = "$actualName" and method().className = "$actualClass" """)
                    .interpret(invokeQueryTarget) shouldBe true
        }
    }
}