package com.github.insanusmokrassar.FSMConfigConverter

import com.github.insanusmokrassar.FSM.core.SimpleState
import com.github.insanusmokrassar.FSM.core.State
import java.util.regex.PatternSyntaxException

val ruleRegex: Regex = Regex("^<((\\\\>)|[^>])+>")
val openRuleRegex: Regex = Regex("^<")
val closeRuleRegex: Regex = Regex(">$")

val multiChoiceRegex = Regex("\\[.+]")
val openMultiChoiceRegex = Regex("^\\[")
val closeMultiChoiceRegex = Regex("]$")
val nonMultiChoiceRegex = Regex("\\\\\\[")

val partsDelimiterRegex: Regex = Regex("^::=")

val rowsDelimiter: Regex = Regex("^[\n\r]+")



private fun String.startsWith(regex: Regex): Boolean {
    return regex.find(this) ?. let {
        startsWith(it.value)
    } ?: false
}

fun compileFromConfig(config: String): List<PreState> {
    val statesRows = ArrayList<PreState>()
    var currentRowRoot: PreState? = null
    var mutableConfig = config
    var wasDelimiter = false
    while (mutableConfig.isNotEmpty()) {
        val state = PreState()
        if (mutableConfig.startsWith(ruleRegex)) {
            val partWithRule = ruleRegex.find(mutableConfig)!!.groupValues.first()
            mutableConfig = mutableConfig.replaceFirst(ruleRegex, "")
            state.text = partWithRule
                    .replaceFirst(openRuleRegex, "")
                    .replaceFirst(closeRuleRegex, "")
            state.isRule = true
        } else {
            if (mutableConfig.startsWith(partsDelimiterRegex) && !wasDelimiter) {
                wasDelimiter = true
                mutableConfig = mutableConfig.replaceFirst(partsDelimiterRegex, "")
                continue
            }
            if (mutableConfig.startsWith(rowsDelimiter)) {
                if (statesRows.last().toRightInRow == null) {
                    state.text = "\\z"
                    state.isRule = false
                    statesRows.last().addRight(
                            state
                    )
                }
                wasDelimiter = false
                mutableConfig = mutableConfig.replaceFirst(rowsDelimiter, "")
                currentRowRoot = null
                continue
            }
            val text: String = if (mutableConfig.startsWith(multiChoiceRegex)) {
                val resultRegex = Regex(multiChoiceRegex.find(mutableConfig)?.value ?: "")
                resultRegex.pattern
            } else {
                if (mutableConfig.startsWith(nonMultiChoiceRegex)) {
                    mutableConfig = mutableConfig.replaceFirst(nonMultiChoiceRegex, "[")
                    "["
                } else {
                    mutableConfig.first() + ""
                }
            }
            state.text = try {
                Regex(text).pattern
            } catch (e: PatternSyntaxException) {
                Regex("\\" + text).pattern
            }
            mutableConfig = mutableConfig.replaceFirst(text, "")
            state.isRule = false
        }
        currentRowRoot ?.addRight(state) ?: {
            currentRowRoot = state
            statesRows.add(state)
        }()
    }
    val preStates = ArrayList<PreState>()
    preStates.addAll(statesRows)
    statesRows.forEach {
        var toRight = it.toRightInRow
        while (toRight != null) {
            preStates.add(toRight)
            toRight = toRight.toRightInRow
        }
    }
    preStates.forEach {
        it.number = preStates.indexOf(it)
    }
    val lookedPositions = ArrayList<Int>()
    preStates.forEachIndexed { index, preState -> preStates.regex(index, lookedPositions) }
    return preStates
}

fun List<PreState>.regex(
        position: Int,
        previousPositions: MutableList<Int>): String {
    val state = get(position)
    if (!previousPositions.contains(position)) {
        previousPositions.add(position)
        if (state.regex.isEmpty()) {
            filter {
                (it.text == state.text && it.isRule && it.number != state.number)
                || (state.isDefinition && state.toRightInRow == it)
            }.forEach {
                it.nextState ?. let {
                    state.regex += regex(indexOf(it), previousPositions)
                } ?: {
                    if (it.isReturn) {
                        state.regex = regexForReturn(position, previousPositions)
                    }
                }()

            }
            state.regex = Regex(state.regex).pattern
        }
    }
    return state.regex
}

private fun List<PreState>.regexForReturn(
        position: Int,
        previousPositions: MutableList<Int>,
        previousReturnPositions: MutableList<Int> = ArrayList()
): String {
    val state = get(position)
    if (previousReturnPositions.contains(position)) {
        return ""
    }
    previousReturnPositions.add(position)
    val definition = state.definition
    var regex = ""
    filter {
        !it.isDefinition && it.isRule && it.text == definition.text
    }.forEach {
        regex += it.toRightInRow ?. let {
            regex(indexOf(it), previousPositions)
        } ?: {
            regexForReturn(indexOf(it), previousPositions, previousReturnPositions)
        }()
    }
    return regex
}

data class PreState internal constructor(
        var isRule: Boolean = true,
        var text: String? = null,
        var number: Int? = null) {

    private var toLeftInRow: PreState? = null

    val state: State?
        get() {
            return if (regex.isEmpty()) {
                null
            } else {
                SimpleState(
                        isAccept,
                        isError,
                        isStack,
                        Regex(regex),
                        nextState?.number
                )
            }
        }

    val nextState: PreState?
        get() {
            return if (toRightInRow == null || isStack) {
                null
            } else {
                toRightInRow
            }
        }

    var toRightInRow: PreState? = null
        private set

    var regex: String = ""
        get() {
            return if (isRule) {
                field
            } else {
                text ?: ""
            }
        }
        set(value) {
            if (isRule) {
                field = regex
            } else {
                throw IllegalStateException("You can't set regex for field manualy")
            }
        }

    val isDefinition: Boolean
        get() = isRule && toLeftInRow == null

    val definition: PreState
        get() = if (isDefinition) {
            this
        } else {
            toLeftInRow ?. definition ?: this
        }

    val isAccept: Boolean
        get() = !isRule

    val isError: Boolean
        get() = !isDefinition || nextState ?.let {
            !(it.isDefinition && it.text == text)
        } ?: false

    val isReturn: Boolean
        get() = !isStack && nextState == null

    val isStack: Boolean
        get() = !isDefinition && isRule

    fun addRight(newRight: PreState) {
        toRightInRow?.let {
            it.addRight(newRight)
            return
        }
        toRightInRow = newRight
        newRight.toLeftInRow = this
    }

    override fun toString(): String {
        return if (isAccept) {
            "A"
        } else {
            "a"
        }.plus(
                if (isError){
                    "E"
                } else {
                    "e"
                }
        ).plus(
                if (isReturn) {
                    "R"
                } else {
                    "r"
                }
        ).plus(
                if (isStack) {
                    "S"
                } else {
                    "s"
                }
        ).plus(
                number ?: "-"
        ).plus(
                " $text"
        )
    }

    fun toString(isNumeric: Boolean = false): String {
        val pretext = if (isRule) {
            "<$text>"
        } else {
            text ?: ""
        }
        return if (isNumeric) {
            "$number$pretext"
        } else {
            pretext
        }
    }

}
