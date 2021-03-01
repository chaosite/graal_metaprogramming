package il.ac.technion.cs.mipphd.graal.domains

import il.ac.technion.cs.mipphd.graal.graphquery.MapFunc
import il.ac.technion.cs.mipphd.graal.graphquery.QueryExecutor
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

data class Item(val node: NodeWrapper?, val expression: String, val statements: String = "") {
    companion object {
        fun default(): Item = Item(null, "", "")
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

    val x: MapFunc<Item> by { nodes: List<NodeWrapper> ->
        val node = nodes.first()
        Item(node, valueNodeToText(node))
    }

    val y: MapFunc<Item> by { nodes: List<NodeWrapper> ->
        val node = nodes.first()
        Item(node, valueNodeToText(node))
    }

    val arithmeticNode: MapFunc<Item> by { nodes: List<NodeWrapper> ->
        val node = nodes.first()
        val xText = state[this::x.name]?.expression
        val yText = state[this::y.name]?.expression
        Item(node, "$xText ${arithmeticNodeToText(node)} $yText")
    }

    private fun valueNodeToText(node: NodeWrapper): String = with(node.node.asNode().toString()) {
        when {
            contains("Constant") -> {
                NodeWrapperUtils.getConstantValue(node)
            }
            contains("ValuePhi") -> {
                "phi${node.id}"
            }
            contains("Parameter") -> {
                "param${node.id}"
            }
            else -> {
                nodeState.getValue(node).expression
            }
        }
    }

    private fun arithmeticNodeToText(node: NodeWrapper): String =
            node.node.toString().replace(Regex("^[^|]*\\|"), "")

    val ifQuery by """
digraph G {
	ifnode [ label="(?P<ifnode>)|is('IfNode')" ];
	cmp [ label="(?P<cmp>)|1 = 1" ];

	cmp -> ifnode [ label="is('DATA') and name() = 'condition'" ];
}
"""

    val cmp: MapFunc<Item> by { nodes: List<NodeWrapper> ->
        val node = nodes.first()
        nodeState.getValue(node)
    }

    val ifnode: MapFunc<Item> by { nodes: List<NodeWrapper> ->
        val node = nodes.first()
        Item(node, state.getValue(this::cmp.name).expression)
    }

    val loopQuery by """
digraph G {
  loopBegin [ label="(?P<loopBegin>)|is('LoopBeginNode')" ];
  loopEnd [ label="(?P<loopEnd>)|is('LoopEndNode')" ];
  someNode [ label="not is('LoopEndNode')" ]
  someNodeKleene [ label="(?P<innerPath>)|not is('LoopEndNode')" ]

  loopBegin -> loopEnd [ label="is('ASSOCIATED') and name() = 'loopBegin'" ];
  loopBegin -> someNode [ label="is('CONTROL')" ]
  someNode -> someNodeKleene [ label="*|is('CONTROL')" ]
  someNodeKleene -> loopEnd [ label="is('CONTROL')"]
}
"""

    val loopBegin: MapFunc<Item> by { nodes: List<NodeWrapper> ->
        Item(nodes.first(), "")
    }
    val loopEnd: MapFunc<Item> by { nodes: List<NodeWrapper> ->
        Item(nodes.first(), "")
    }
    val innerPath: MapFunc<Item> by { nodes: List<NodeWrapper> ->
        val condition = nodes.zipWithNext().mapNotNull { (node, next) ->
            if (node.node.toString().contains("If")) {
                val edge = graph.getEdge(node, next)
                when (edge.name) {
                    "trueSuccessor" -> {
                        nodeState.getValue(node).expression
                    }
                    "falseSuccessor" -> {
                        "!(${nodeState.getValue(node).expression})"
                    }
                    else -> throw RuntimeException("Unhandled edge! $edge")
                }
            } else {
                null
            }
        }.joinToString(" && ")
        val phis = graph.vertexSet().filter { it.node.toString().contains("ValuePhi") }
        val relatedPhis = phis.filter { graph.containsEdge(state.getValue(this::loopBegin.name).node, it) }
        val values = relatedPhis.joinToString("\n") { phi ->
            val edge = graph.incomingEdgesOf(phi).filter { it.name.startsWith("from ") }
                .filterIsInstance<PhiEdgeWrapper>().find { it.from == state.getValue(this::loopEnd.name).node }
            val valueNode = graph.getEdgeSource(edge)
            "${nodeState.getValue(phi).expression} := ${nodeState.getValue(valueNode).expression}"
        }

        // Make the text thing
        state[this::innerPath.name] = state.getValue(this::innerPath.name).copy(statements = """
${state.getValue(this::loopEnd.name).node?.id}:
   assume $condition
${values.prependIndent("   ")}
   goto ${state.getValue(this::loopBegin.name).node?.id}
""".trimIndent()
        )

        null // Return null so nothing gets updated
    }

    fun merge(result: Result): String {
        val data = result.groups.filter { it.contains(this::innerPath.name) }
        val ends = data.groupBy { it.getValue(this::loopBegin.name).node?.id }
            .mapValues { v -> v.value.map { it.getValue(this::loopEnd.name).node?.id } }
        val loopBlocks = ends.map {
            """
${it.key}:
    goto ${it.value.joinToString(separator = ", ")}
""".trimIndent()
        }.joinToString(separator = "\n")
        return loopBlocks + "\n" + data.map { it[this::innerPath.name]?.statements }.joinToString(separator = "\n")
    }
}