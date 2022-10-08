package il.ac.technion.cs.mipphd.graal.domains

import il.ac.technion.cs.mipphd.graal.graphquery.CaptureGroupQuery
import il.ac.technion.cs.mipphd.graal.graphquery.QueryExecutor
import il.ac.technion.cs.mipphd.graal.graphquery.WholeMatchQuery
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
    val relatedValues: List<NodeWrapper> = listOf(),
    val nextIds: Set<Int> = setOf(),
    val mergeValues: Map<NodeWrapper, Map<NodeWrapper, NodeWrapper>> = mapOf(),
    val mergeAssignments: Map<NodeWrapper, Map<String, String>> = mapOf()
) {
    companion object {
        fun default(): Item = Item("")
    }
}

class McCarthy91Analysis(graph: GraalAdapter) : QueryExecutor<Item>(graph, Item::default) {
    val arithmeticQuery by WholeMatchQuery(
        """
digraph G {
	arith [ label="(?P<arithmeticNode>)|" ];
	x [ label="(?P<x>)|" ];
	y [ label="(?P<y>)|" ];

	x -> arith [ label="is('DATA') and name() = 'x'" ];
	y -> arith [ label="is('DATA') and name() = 'y'" ];
}
"""
    ) { captureGroups: Map<String, List<NodeWrapper>> ->
        val node = captureGroups.getValue("arithmeticNode").first()
        val x = captureGroups.getValue("x").first()
        val y = captureGroups.getValue("y").first()
        val xText = state.getValue(x).expression
        val yText = state.getValue(y).expression
        state[node] = Item("$xText ${arithmeticNodeToText(node)} $yText")
    }

    private fun arithmeticNodeToText(node: NodeWrapper): String = node.node.toString().replace(Regex("^[^|]*\\|"), "")

    val constantQuery by CaptureGroupQuery("""
digraph G {
    n [ label = "(?P<constant>)|is('ConstantNode')" ];
}
""", "constant" to  { nodes: List<NodeWrapper> ->
        Item(expression = NodeWrapperUtils.getConstantValue(nodes.first()))
    })

    val valuePhiQuery by CaptureGroupQuery("""
digraph G {
    valuephi [ label = "(?P<valuephi>)|is('ValuePhiNode')" ];
}
""", "valuephi" to { nodes: List<NodeWrapper> ->
        Item(expression = "phi${nodes.first().id}")
    })

    val valueProxyQuery by WholeMatchQuery(
        """
        digraph G {
            value [ label = "(?P<value>)|1 = 1" ];
            valueProxy [ label = "(?P<valueProxy>)|is('ValueProxyNode')" ];
            
            value -> valueProxy [ label = "is('DATA') and name() = 'value'" ];
        }
    """.trimIndent()
    ) { captures: Map<String, List<NodeWrapper>> ->
        val proxy = captures.getValue("valueProxy").first()
        val value = captures.getValue("value").first()

        state[proxy] = state.getValue(proxy).copy(expression = state.getValue(value).expression)
    }

    val parameterQuery by CaptureGroupQuery("""
digraph G {
    n [ label = "(?P<parameter>)|is('ParameterNode')" ];
}
""", "parameter" to { nodes: List<NodeWrapper> ->
        Item(expression = "parameter${nodes.first().id}")
    })


    val ifConditionQuery by WholeMatchQuery(
        """
digraph G {
	ifnode [ label="(?P<ifnode>)|is('IfNode')" ];
	cmp [ label="(?P<ifcondition>)|" ];

	cmp -> ifnode [ label="is('DATA') and name() = 'condition'" ];
}
"""
    ) { captureGroups: Map<String, List<NodeWrapper>> ->
        val node = captureGroups.getValue("ifnode").first()
        val condition = captureGroups.getValue("ifcondition").first()
        state[node] = Item(state.getValue(condition).expression)
    }

    val ifPathQuery by WholeMatchQuery(
        """
digraph G {
    ifnode [ label="(?P<ifpathnode>)|is('IfNode')" ];
    truepath [ label="(?P<truepath>)|" ];
    falsepath [ label="(?P<falsepath>)|" ];

    ifnode -> truepath [ label="is('CONTROL') and name() = 'trueSuccessor'" ];
    ifnode -> falsepath [ label="is('CONTROL') and name() = 'falseSuccessor'" ];
}
"""
    ) { captureGroups: Map<String, List<NodeWrapper>> ->
        val ifNode = captureGroups.getValue("ifpathnode").first()
        val nextTrue = captureGroups.getValue("truepath").first()
        val nextFalse = captureGroups.getValue("falsepath").first()

        state[nextTrue] = state.getValue(nextTrue).copy(condition = state.getValue(ifNode).expression)
        state[nextFalse] = state.getValue(nextFalse).copy(condition = "!(${state.getValue(ifNode).expression})")
    }

    val frameStateQuery by WholeMatchQuery(
        """
digraph G {
	framestate [ label="is('FrameState')" ];
	merge [ label="(?P<mergenode>)|is('AbstractMergeNode')" ];
	values [ label="[](?P<phivalues>)|" ];
    sourcevalues [ label="[](?P<phisourcevalues>)|" ];

	values -> framestate [ label = "is('DATA') and name() = 'values'" ];
    merge -> values [ label = "name() = 'merge'" ];
    sourcevalues -> values [ label = "name() != 'merge'" ];
	framestate -> merge [ label = "is('DATA') and name() = 'stateAfter'" ];
}
"""
    ) { captureGroups: Map<String, List<NodeWrapper>> ->
        val mergeNode = captureGroups.getValue("mergenode").first()
        val values = captureGroups.getValue("phivalues")
        val sourceValues = captureGroups.getValue("phisourcevalues")

        val map = mutableMapOf<NodeWrapper, MutableMap<NodeWrapper, NodeWrapper>>()
        val assignments = mutableMapOf<NodeWrapper, MutableMap<String, String>>()
        for ((n, s) in state.getValue(mergeNode).mergeAssignments) {
            assignments[n] = HashMap(s)
        }
        for (phi in values) {
            for (value in sourceValues) {
                val edge = graph.getEdge(value, phi) as PhiEdgeWrapper? // TODO: Capture from graph
                if (edge != null) {
                    assignments.computeIfAbsent(edge.from) { mutableMapOf() }[state.getValue(phi).expression] =
                        state.getValue(value).expression
                }
                /*                val edge = graph.getEdge(value, phi) as PhiEdgeWrapper?
                                if (edge != null) {
                                    if (edge.from !in map) {
                                        map[edge.from] = mutableMapOf()
                                    }
                                    map.getValue(edge.from)[phi] = value
                                } */
            }
        }
        state[mergeNode] =
            state.getValue(mergeNode).copy(relatedValues = values, mergeValues = map, mergeAssignments = assignments)
    }

    val loopQuery by WholeMatchQuery(
        """
digraph G {
  loopPrev  [ label="(?P<loopPrev>)|not is ('LoopEndNode')" ];
  loopBegin [ label="(?P<loopBegin>)|is('LoopBeginNode')" ];
  loopEnd [ label="(?P<loopEnd>)|is('LoopExitNode') or is('LoopEndNode')" ];
  someNode [ label="(?P<firstInPath>)|not is('LoopEndNode') and not is('LoopExitNode')" ]
  someNodeKleene [ label="(?P<innerPath>)|not is('LoopEndNode') and not is('LoopExitNode')" ]

  loopPrev -> loopBegin [ label="is('CONTROL')" ];
  loopBegin -> loopEnd [ label="is('ASSOCIATED') and name() = 'loopBegin'" ];
  loopBegin -> someNode [ label="is('CONTROL')" ];
  someNode -> someNodeKleene [ label="*|is('CONTROL')" ];
  someNodeKleene -> loopEnd [ label="is('CONTROL')"];
}
"""
    ) { captureGroups: Map<String, List<NodeWrapper>> ->
        val begin = captureGroups.getValue("loopBegin").first()
        val prev = captureGroups.getValue("loopPrev").first()
        val end = captureGroups.getValue("loopEnd").first()
        val nodes = captureGroups.getValue("firstInPath") + captureGroups.getValue("innerPath") + end

        val condition =
            nodes.asSequence().map(state::getValue).map(Item::condition).filter(String::isNotEmpty).joinToString(" && ")

        val next = if (state.getValue(end).nextIds.isNotEmpty()) state.getValue(end).nextIds.first() else "???"
        /* val values =
            state[begin]?.mergeValues?.let { it[end] ?: it[prev] }?.entries?.joinToString("\n") { (phi, valueNode) ->
                "${state.getValue(phi).expression} := ${state.getValue(valueNode).expression}"
            } ?: "" */
        val values =
            state.getValue(begin).mergeAssignments[end]?.entries?.joinToString(";\n") { (k, v) -> "$k := $v" } ?: ""
        // Make the text thing
        state[end] = state.getValue(end).copy(
            statements = """
${end.id}:
    assume $condition;
${values.prependIndent("    ")}
    goto $next
""".trimIndent()
        )
    }

    val loopBeginQuery by WholeMatchQuery(
        """
digraph G {
        loopBegin [ label="(?P<begin>)|is('LoopBeginNode')" ];
        loopEnd [ label="[](?P<end>)|is('LoopEndNode') or is('LoopExitNode')" ];
        
        loopBegin -> loopEnd [ label="is('ASSOCIATED')" ];
}
    """
    ) { captureGroups: Map<String, List<NodeWrapper>> ->
        val begin = captureGroups.getValue("begin").first()
        val ends = captureGroups.getValue("end")

        state[begin] = state[begin]!!.copy(
            statements = """
            ${begin.id}:
                goto ${ends.joinToString(", ") { it.id.toString() }}
        """.trimIndent()
        )
    }

    val loopNextQuery by WholeMatchQuery(
        """
digraph G {
    loopEnd [ label="(?P<loopEnd>)|1=1" ];
    merge [ label="(?P<merge>)|1=1" ];
    next [ label="(?P<next>)|1=1" ];
    
    merge -> loopEnd [ label="is('ASSOCIATED')" ];
    loopEnd -> next [ label="is('CONTROL')" ];
}
    """
    ) { capture: Map<String, List<NodeWrapper>> ->
        val end = capture.getValue("loopEnd").first()
        val next = capture.getValue("next").first()

        state[end] = state.getValue(end).copy(nextIds = setOf(next.id))
    }

    val mergePathQuery by WholeMatchQuery(
        """
digraph G {
    mergeBegin [ label="(?P<mergeBegin>)|is('StartNode') or is('AbstractMergeNode') or is('LoopExitNode')" ];
    someNode [ label="(?P<mergePath>)|not is('AbstractMergeNode') and not is ('ReturnNode') and not is('LoopEndNode') and not is ('LoopExitNode')" ];
    mergeEnd [ label="(?P<mergeEnd>)|is('AbstractMergeNode')" ];
    
    mergeBegin -> someNode [ label="*|is('CONTROL')" ];
    someNode -> mergeEnd [ label="is('CONTROL')" ];
}
    """
    ) { captureGroupActions: Map<String, List<NodeWrapper>> ->
        val begin = captureGroupActions.getValue("mergeBegin").first()
        val end = captureGroupActions.getValue("mergeEnd").first()
        val nodes = listOf(begin) + captureGroupActions.getValue("mergePath")
        val lastNode = nodes.last()

        val condition =
            nodes.asSequence().map(state::getValue).map(Item::condition).filter(String::isNotEmpty).joinToString(" && ")
        /* val values =
            state.getValue(end).mergeValues[nodes.last()]?.entries?.joinToString("\n") { (phi, valueNode) ->
                "${state.getValue(phi).expression} := ${state.getValue(valueNode).expression}"
            } ?: "" */

        val values =
            state.getValue(end).mergeAssignments[lastNode]?.entries?.joinToString(";\n") { (k, v) -> "$k := $v" }
                ?: "???"
        val nextIds = state.getValue(begin).nextIds + lastNode.id

        state[begin] = state.getValue(begin).copy(
            nextIds = nextIds, statements = """
            ${begin.id}:
                ${nextIds.joinToString(", ", "goto ") { it.toString() }}
        """.trimIndent()
        )
        val assume = if (condition.isNotEmpty()) "assume $condition;" else ""
        state[lastNode] = state.getValue(lastNode).copy(
            statements = """
${lastNode.id}:
    $assume
${values.prependIndent("    ")}
    goto ${end.id}
""".trimIndent()
        )
    }

    val returnNodeQuery by WholeMatchQuery(
        """
digraph G {
    r [ label = "(?P<returnNode>)|is('ReturnNode')" ];
    v [ label = "(?P<value>)|" ];
    
    v -> r [ label = "is('DATA')" ];
}
    """
    ) { captureGroups: Map<String, List<NodeWrapper>> ->
        val returnNode = captureGroups.getValue("returnNode").first()
        val value = captureGroups.getValue("value").first()

        state[returnNode] = state.getValue(returnNode).copy(
            statements = """
            ${returnNode.id}:
                return ${state.getValue(value).expression}
        """.trimIndent()
        )
    }
}