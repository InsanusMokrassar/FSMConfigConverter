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

fun compileFromConfig(config: String): List<PreviewState> {
    var previousRowRoot: PreviewState? = null
    var currentRowRoot: PreviewState? = null
    var mutableConfig = config
    var wasDelimiter = false
    while (mutableConfig.isNotEmpty()) {
        val state = PreviewState()
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
                if (currentRowRoot!!.toRightInRow == null) {
                    state.text = "\\z"
                    state.isRule = false
                    currentRowRoot.addRight(
                            state
                    )
                }
                wasDelimiter = false
                mutableConfig = mutableConfig.replaceFirst(rowsDelimiter, "")
                previousRowRoot = currentRowRoot
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
        currentRowRoot ?. addRight(state) ?: {
            if (previousRowRoot != null) {
                previousRowRoot!!.lower = state
            }
            currentRowRoot = state
        }()
    }
    return (currentRowRoot ?: previousRowRoot!!).toList().filterIndexed {
        index, previewState ->
        previewState.number = index
        true
    }
}

fun List<PreviewState>.findUsages(definitionState: PreviewState): List<PreviewState> {
    return filter {
        !it.isDefinition && it.isRule && definitionState.text == it.text
    }
}

fun PreviewState.findDefinitions(): List<PreviewState> {
    val root = definition.top
    var current: PreviewState? = root
    val definitions = ArrayList<PreviewState>()
    while (current != null) {
        current = if (current.isDefinition) {
            if (current.text == text) {
                definitions.add(current)
            }
            current.lower
        } else {
            null
        }
    }
    return definitions
}

data class PreviewState internal constructor(
        var isRule: Boolean = true,
        var text: String? = null,
        var number: Int? = null) {

    private var toLeftInRow: PreviewState? = null

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

    val nextState: PreviewState?
        get() {
            return if (isRule && (toRightInRow == null || isStack)) {
                this.findDefinitions().first()
            } else {
                toRightInRow
            }
        }

    var toRightInRow: PreviewState? = null
        private set

    private var toTop: PreviewState? = null

    val top: PreviewState
        get() {
            return toTop ?. top ?: {
                this
            }()
        }

    var lower: PreviewState? = null
        get() {
            return if (isDefinition) {
                field
            } else {
                throw IllegalStateException("The state is not definition")
            }
        }
        set(value) {
            if (isDefinition) {
                field = value
                value ?. toTop = this
            } else {
                throw IllegalStateException("The state is not definition")
            }
        }

    var regex: String = ""
        get() {
            return if (field.isEmpty()) {
                field = if (isRule) {
                    if (isDefinition) {
                        nextState!!.regex
                    } else {
                        var resultField = ""
                        findDefinitions().forEach {
                            resultField += "|(${it.regex})"
                        }
                        resultField.replaceFirst("|", "")
                    }
                } else {
                    if (text == "\\z") {
                        toList().findUsages(definition).getNextRegex()
                    } else {
                        text ?: ""
                    }
                }
                field = Regex(field).pattern
                field
            } else {
                field
            }

        }
        set(value) {
            if (isRule) {
                field = regex
            } else {
                throw IllegalStateException("You can't set regex for field manualy")
            }
        }

    private fun List<PreviewState>.getNextRegex(wasIn: MutableList<PreviewState> = ArrayList()): String {
        val regexBuilder = StringBuilder()
        forEach {
            if (!wasIn.contains(it)) {
                wasIn.add(it)
                it.toRightInRow ?. let {
                    regexBuilder.append(it.regex)
                } ?: {
                    regexBuilder.append(
                            it.toList().findUsages(it.definition).getNextRegex(wasIn)
                    )
                }()
            }
        }
        return Regex(regexBuilder.toString()).pattern
    }

    val isDefinition: Boolean
        get() = isRule && toLeftInRow == null

    val definition: PreviewState
        get() = if (isDefinition) {
            this
        } else {
            toLeftInRow ?. definition ?: this
        }

    val isAccept: Boolean
        get() = !isRule

    val isError: Boolean
        get() = !isDefinition || lower ?.let {
            !(it.isDefinition && it.text == text)
        } ?: true

    val isReturn: Boolean
        get() = !isStack && nextState == null

    val isStack: Boolean
        get() = !isDefinition && isRule && toRightInRow != null

    fun addRight(newRight: PreviewState) {
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


    //TODO:: optimise
    fun toList(): List<PreviewState> {
        val preStates = ArrayList<PreviewState>()
        val currentRoot = definition.top
        var currentDefinition: PreviewState? = currentRoot
        while (currentDefinition != null) {
            preStates.add(currentDefinition)
            currentDefinition = currentDefinition.lower
        }
        currentDefinition = currentRoot
        while (currentDefinition != null) {
            var currentRightState: PreviewState? = currentDefinition.toRightInRow
            while (currentRightState != null) {
                preStates.add(currentRightState)
                currentRightState = currentRightState.toRightInRow
            }
            currentDefinition = currentDefinition.lower
        }
        return preStates
    }
}

/*


fun List<PreviewState>.regex(
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

private fun List<PreviewState>.regexForReturn(
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
 */