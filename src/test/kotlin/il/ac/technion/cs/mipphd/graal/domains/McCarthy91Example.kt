package il.ac.technion.cs.mipphd.graal.domains

import il.ac.technion.cs.mipphd.graal.graphquery.CaptureGroupAction
import il.ac.technion.cs.mipphd.graal.graphquery.QueryExecutor
import il.ac.technion.cs.mipphd.graal.graphquery.WholeMatchAction
import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapperUtils
import il.ac.technion.cs.mipphd.graal.utils.PhiEdgeWrapper

fun mccarthy91(l: Long): Long {
    var n = l
    var c = 1
    while (c != 0) {
        c -= 1
        if (n > 100) {
            n -= 10
        } else {
            n += 11
            c += 2
        }
    }
    return n
}

data class Item(
    val expression: String,
    val statements: String = "",
    val condition: String = "",
    val relatedValues: List<NodeWrapper> = listOf()
) {
    companion object {
        fun default(): Item = Item("")
    }
}

class McCarthy91Analysis(graph: GraalAdapter) : QueryExecutor<Item>(graph, Item::default) {
    val arithmeticQuery by """
digraph G {
	arith [ label="(?P<arithmeticNode>)|1 = 1" ];
	x [ label="(?P<x>)|1 = 1" ];
	y [ label="(?P<y>)|1 = 1" ];

	x -> arith [ label="is('DATA') and name() = 'x'" ];
	y -> arith [ label="is('DATA') and name() = 'y'" ];
}
"""

    val arithmeticQueryAction: WholeMatchAction by { captureGroups: Map<String, List<NodeWrapper>> ->
        val node = captureGroups.getValue("arithmeticNode").first()
        val x = captureGroups.getValue("x").first()
        val y = captureGroups.getValue("y").first()
        val xText = state.getValue(x).expression
        val yText = state.getValue(y).expression
        state[node] = Item("$xText ${arithmeticNodeToText(node)} $yText")
    }

    private fun arithmeticNodeToText(node: NodeWrapper): String =
        node.node.toString().replace(Regex("^[^|]*\\|"), "")

    val constantQuery by """
digraph G {
    n [ label = "(?P<constant>)|is('ConstantNode')" ];
}
"""

    val constant: CaptureGroupAction<Item> by { nodes: List<NodeWrapper> ->
        Item(expression = NodeWrapperUtils.getConstantValue(nodes.first()))
    }

    val valuePhiQuery by """
digraph G {
    valuephi [ label = "(?P<valuephi>)|is('ValuePhiNode')" ];
}
"""

    val valuephi: CaptureGroupAction<Item> by { nodes: List<NodeWrapper> ->
        Item(expression = "phi${nodes.first().id}")
    }

    val parameterQuery by """
digraph G {
    n [ label = "(?P<parameter>)|is('ParameterNode')" ];
}
"""

    val parameter: CaptureGroupAction<Item> by { nodes: List<NodeWrapper> ->
        Item(expression = "parameter${nodes.first().id}")
    }


    val ifConditionQuery by """
digraph G {
	ifnode [ label="(?P<ifnode>)|is('IfNode')" ];
	cmp [ label="(?P<ifcondition>)|1 = 1" ];

	cmp -> ifnode [ label="is('DATA') and name() = 'condition'" ];
}
"""
    val ifConditionQueryAction: WholeMatchAction by { captureGroups: Map<String, List<NodeWrapper>> ->
        val node = captureGroups.getValue("ifnode").first()
        val condition = captureGroups.getValue("ifcondition").first()
        state[node] = Item(state.getValue(condition).expression)
    }

    val ifPathQuery by """
digraph G {
    ifnode [ label="(?P<ifpathnode>)|is('IfNode')" ];
    truepath [ label="(?P<truepath>)|1 = 1" ];
    falsepath [ label="(?P<falsepath>)|1 = 1" ];

    ifnode -> truepath [ label="is('CONTROL') and name() = 'trueSuccessor'" ];
    ifnode -> falsepath [ label="is('CONTROL') and name() = 'falseSuccessor'" ];
}
"""

    val ifPathQueryAction: WholeMatchAction by { captureGroups: Map<String, List<NodeWrapper>> ->
        val ifNode = captureGroups.getValue("ifpathnode").first()
        val nextTrue = captureGroups.getValue("truepath").first()
        val nextFalse = captureGroups.getValue("falsepath").first()

        state[nextTrue] = state.getValue(nextTrue).copy(condition = state.getValue(ifNode).expression)
        state[nextFalse] =
            state.getValue(nextFalse).copy(condition = "!(${state.getValue(ifNode).expression})")
    }

    val frameStateQuery by """
digraph G {
	framestate [ label="is('FrameState')"];
	merge [ label="(?P<mergenode>)|1 = 1"]
	values [ label="[](?P<phivalues>)|1 = 1"]

	values -> framestate [ label = "is('DATA') and name() = 'values'"];
	framestate -> merge [ label = "is('DATA') and name() = 'stateAfter'"];
}
"""

    val frameStateQueryAction: WholeMatchAction by { captureGroups: Map<String, List<NodeWrapper>> ->
        val mergeNode = captureGroups.getValue("mergenode").first()
        val values = captureGroups.getValue("phivalues")

        state[mergeNode] = state.getValue(mergeNode).copy(relatedValues = values)
    }

    val loopQuery by """
digraph G {
  loopBegin [ label="(?P<loopBegin>)|is('LoopBeginNode')" ];
  loopEnd [ label="(?P<loopEnd>)|is('LoopExitNode') or is('LoopEndNode')" ];
  someNode [ label="not is('LoopEndNode') and not is('LoopExitNode')" ]
  someNodeKleene [ label="(?P<innerPath>)|not is('LoopEndNode') and not is('LoopExitNode')" ]

  loopBegin -> loopEnd [ label="is('ASSOCIATED') and name() = 'loopBegin'" ];
  loopBegin -> someNode [ label="is('CONTROL')" ]
  someNode -> someNodeKleene [ label="*|is('CONTROL')" ]
  someNodeKleene -> loopEnd [ label="is('CONTROL')"]
}
"""
    val loopQueryAction: WholeMatchAction by { captureGroups: Map<String, List<NodeWrapper>> ->
        val begin = captureGroups.getValue("loopBegin").first()
        val end = captureGroups.getValue("loopEnd").first()
        val nodes = captureGroups.getValue("innerPath")
        val condition = nodes.asSequence().map(state::getValue).map(Item::condition).filter(String::isNotEmpty)
            .joinToString(" && ")
        val values = state.getValue(begin).relatedValues.joinToString("\n") { phi ->
            val edge = graph.incomingEdgesOf(phi).filter { it.name.startsWith("from ") }
                .filterIsInstance<PhiEdgeWrapper>().find { it.from == end }
            if (edge != null) {
                val valueNode = graph.getEdgeSource(edge)
                "${state.getValue(phi).expression} := ${state.getValue(valueNode).expression}"
            } else ""
        }

        // Make the text thing
        state[end] = state.getValue(end).copy(
            statements = """
${end.id}:
   assume $condition
${values.prependIndent("   ")}
   goto ${begin.id}
""".trimIndent()
        )
    }
}