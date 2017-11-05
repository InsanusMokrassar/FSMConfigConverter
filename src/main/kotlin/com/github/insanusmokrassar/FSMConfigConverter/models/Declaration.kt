package com.github.insanusmokrassar.FSMConfigConverter.models

import com.github.insanusmokrassar.FSM.core.State
import com.github.insanusmokrassar.FSM.core.StateAction
import com.github.insanusmokrassar.FSM.core.defaultAction

class Declaration(
        val name: String,
        val previousDeclaration: Declaration? = null
): State {

    var nextDeclaration: Declaration? = null
    var declarationStartState: CommonState? = null

    val number: Int
        get() = previousDeclaration ?. let { it.number + 1 } ?: 0

    override val accept: Boolean = false
    override val error: Boolean
        get() = nextDeclaration ?. name != name
    override val next: Int?
        get() = declarationStartState ?. number
    override val regex: Regex
        get() = declarationStartState ?. regex ?: emptyRegex
    override val stack: Boolean = false
    override val action: StateAction = defaultAction

    fun addRule(name: String) {
        addState(Rule(name, this))
    }

    fun addSimpleState(regex: Regex) {
        addState(SimpleState(regex, this))
    }

    private fun addState(state: CommonState) {
        declarationStartState ?. let {
            it.lastState().toRightInRow = state
        } ?: {
            declarationStartState = state
        }()
    }
}

fun Declaration.root(): Declaration {
    var current = this
    while (current.previousDeclaration != null) {
        current = current.previousDeclaration ?: current
    }
    return current
}

fun Declaration.declarationsList(): List<Declaration> {
    var current: Declaration? = root()
    val result = ArrayList<Declaration>()
    while (current != null) {
        result.add(current)
        current = current.nextDeclaration
    }
    return result
}

fun Declaration.usages(): List<Rule> {
    val result = ArrayList<Rule>()
    declarationsList().forEach {
        it.declarationStartState ?. let {
            it.statesRowList().filter {
                it is Rule && it.name == name
            }.mapTo(
                    result,
                    { it as Rule }
            )
        }
    }
    return result
}

fun Declaration.toStatesList(): List<State> {
    val result = ArrayList<State>()
    val declarations = declarationsList()
    result.addAll(declarations)
    declarations.forEach {
        result.addAll(it.declarationStartState !!. statesRowList())
    }
    return result
}
