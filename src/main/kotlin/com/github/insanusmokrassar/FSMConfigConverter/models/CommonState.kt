package com.github.insanusmokrassar.FSMConfigConverter.models

import com.github.insanusmokrassar.FSM.core.State
import com.github.insanusmokrassar.FSM.core.StateAction
import com.github.insanusmokrassar.FSM.core.defaultAction


abstract class CommonState(
        val declaration: Declaration
): State {
    var toLeftInRow: CommonState? = null
        private set
    val number: Int
        get() {
            return toLeftInRow ?.let { it.number + 1 } ?: if (declaration.previousDeclaration == null) {
                declaration.declarationsList().last().number + 1
            } else {
                declaration.previousDeclaration.declarationStartState ?. let {
                    it.statesRowList().last().number + 1
                } ?: -1
            }
        }
    var toRightInRow: CommonState? = null
        set (value) {
            value ?. let {
                it.toLeftInRow = this
            } ?: field ?. let {
                if (it.toLeftInRow == this) {
                    it.toLeftInRow = null
                }
            }
            field = value
        }
    abstract val isReturn: Boolean
    override val action: StateAction = defaultAction
}

fun CommonState.lastState(): CommonState {
    var current = this
    while (true) {
        current.toRightInRow ?. let {
            current = it
        } ?: return current
    }
}

fun CommonState.canBeInStack(): Boolean = declaration.usages().firstOrNull { it.stack } != null

fun CommonState.statesRowList(): List<CommonState> {
    var current: CommonState? = declaration.declarationStartState
    val result = ArrayList<CommonState>()
    while (current != null) {
        result.add(current)
        current = current.toRightInRow
    }
    return result
}

fun CommonState.calculateRegexForEpsilon(
        wasIn: MutableList<CommonState> = ArrayList()
): String? {
    if (wasIn.contains(this)) {
        return ""
    }
    wasIn.add(this)
    return toRightInRow ?. regex ?. pattern
            ?: declaration
            .usages().mapNotNull { it.calculateRegexForEpsilon(wasIn) }
            .joinToString("|", "(", ")")
}
