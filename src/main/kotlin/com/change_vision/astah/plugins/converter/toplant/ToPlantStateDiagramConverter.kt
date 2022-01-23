package com.change_vision.astah.plugins.converter.toplant

import com.change_vision.jude.api.inf.model.*
import com.change_vision.jude.api.inf.presentation.ILinkPresentation
import com.change_vision.jude.api.inf.presentation.INodePresentation


object ToPlantStateDiagramConverter {
	var choce_num : Int = 0
	// < vertex,getId(), choce_num>
	var choice_list = mutableMapOf<String, Int>()
	get() {
		return field
	}
	set(value) {
		field = value
	}

	fun convert(diagram: IStateMachineDiagram, sb: StringBuilder) {
        val rootVertexes =
            diagram.presentations
                .filterIsInstance<INodePresentation>().map { it.model }
                .filterIsInstance<IVertex>().filter { it.container !is IVertex }

        val transitions = diagram.presentations
            .filterIsInstance<ILinkPresentation>().map { it.model }
            .filterIsInstance<ITransition>()

        rootVertexes.forEach { vertexConvert(it, sb, "", transitions) }
	/*
        transitions.forEach { transitionConvert(it, sb) }
	*/
    }


    private fun vertexConvert(vertex: IVertex, sb: StringBuilder, indent: String, transition: List<ITransition>) {
        when (vertex) {
            is IFinalState -> { /* Skip */
            }
            is IPseudostate -> { /* Skip */
			if (vertex.isChoicePseudostate()) {
				/*
				if(choice_list.containsKey(vertex.getId())) {
				}*/
				sb.appendLine("${indent}state choice${choce_num} <<choice>>")
				choice_list[vertex.getId()] = choce_num
				choce_num += 1
			}
            }
            is IState -> {
                when {
                    vertex.subvertexes.isEmpty() -> sb.appendLine("${indent}state ${vertex.name}")
                    else -> {
                        sb.appendLine("${indent}state ${vertex.getFullName("_")} {")
			println("### ${vertex.getRegionSize()}")
			for(i in 0..vertex.getRegionSize()-1) {
                        	vertex.getSubvertexes(i)?.forEach { vertexConvert(it, sb, "$indent  ", transition) }

				// 領域内の遷移を出力
				transition.forEach {
					//if (vertex.getSubvertexes(i).contains(it.source) || vertex.getSubvertexes(i).contains(it.target)) {
					//	sb.appendLine("${it.source} --> ${it.target}")
					//	println("match ${it.source} , ${it.target}")
					//}
					//else {
					//	println("nm ${vertex}, ${it.source} , ${it.target}")
					//}
					if (vertex.getSubvertexes(i).contains(it.source) || vertex.getSubvertexes(i).contains(it.target)) {
						transitionConvert(it, sb)
					}
				}

				// 領域の境界
				if(i != vertex.getRegionSize()-1) {
					sb.appendLine("--")
				}
			}
                        sb.appendLine("${indent}}")
                    }
                }
            }
        }
    }

    private fun transitionConvert(transition: ITransition, sb: java.lang.StringBuilder) {
        sb.append(
            //when (val source = transition.source) {
            //val source = transition.source
            when (val source = transition.source) {
		    /*
                is IPseudostate -> {
			if (source.isInitialPseudostate()) "[*]" else "XXX" 
		}
		*/
		is IPseudostate -> {
			if (source.isInitialPseudostate()) "[*]" else 
			if (source.isChoicePseudostate()) "choice${choice_list[source.getId()]}" else ""
		}
                is IFinalState -> "[*]"
                is IState -> source.name
                else -> "[*]"
            }
        )
        sb.append(" --> ")
        sb.append(
            when (val target = transition.target) {
                is IPseudostate -> {
			if (target.isInitialPseudostate()) "[*]" else
			if (target.isChoicePseudostate()) "choice${choice_list[target.getId()]}" else ""
		}
                is IFinalState -> "[*]"
                is IState -> target.name
                else -> "[*]"
            }
        )
        val label =
            transition.event.let { if (it.isNotBlank()) it else "" } +
                    transition.guard.let { if (it.isNotBlank()) "[$it]" else "" } +
                    transition.action.let { if (it.isNotBlank()) "/$it" else "" }
        if (label.isNotBlank()) sb.append(": $label")
        sb.appendLine()
    }
}
