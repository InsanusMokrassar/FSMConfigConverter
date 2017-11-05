package com.github.insanusmokrassar.FSMConfigConverter

import com.github.insanusmokrassar.FSM.core.State
import com.github.insanusmokrassar.FSM.core.StateAction
import com.github.insanusmokrassar.FSM.core.defaultAction

val emptyRegex = Regex("^$")
val redundantOrRegex = Regex("\\|{2,}")
val redundantRegex = Regex("\\(\\|*\\)")
val redundantOrInTheEndRegex = Regex("\\|+\\)")

private fun String.clearRegex(): String {
    var regex = this
    while (redundantRegex.find(regex) != null) {
        regex = redundantRegex.replace(regex, "")
        if (redundantOrRegex.find(regex) != null) {
            regex = redundantOrRegex.replace(regex, "|")
        }
        if (redundantOrInTheEndRegex.find(regex) != null) {
            regex = redundantOrInTheEndRegex.replace(regex, ")")
        }
    }
    return regex
}

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

open class Rule(
        val name: String,
        declaration: Declaration
) : CommonState(declaration) {
    override val accept: Boolean = false
    override val error: Boolean = true
    override val next: Int?
        get() = findDeclarations().first().number
    override val regex: Regex
        get() = Regex(
                findDeclarations().joinToString("|", "(", ")") {
                    "(${it.regex.pattern})"
                }.clearRegex()
        )
    override val stack: Boolean
        get() = toRightInRow != null
    override val isReturn: Boolean = false
}

fun Rule.findDeclarations(): List<Declaration> {
    var current: Declaration? = declaration.root()
    val result = ArrayList<Declaration>()
    while (current != null) {
        if (current.name == name) {
            result.add(current)
        }
        current = current.nextDeclaration
    }
    return result
}

class SimpleState(
        private val realRegex: Regex,
        declaration: Declaration
) : CommonState(
        declaration
) {
    override val regex: Regex
        get() {
            return if (isEpsilon) {
                var regex = calculateRegexForEpsilon() ?. clearRegex() ?: ""
                if (regex.isEmpty()) {
                    regex = emptyRegex.pattern
                }
                Regex(regex)
            } else {
                realRegex
            }
        }

    override val isReturn: Boolean
        get() = toRightInRow == null && canBeInStack()
    override val accept: Boolean
        get() = !(isEpsilon && isReturn)
    override val error: Boolean = true
    override val next: Int?
        get() = toRightInRow ?. number
    override val stack: Boolean = false

    val isEpsilon: Boolean
        get() = realRegex.pattern == emptyRegex.pattern
                && toRightInRow == null
                && toLeftInRow == null
}

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
