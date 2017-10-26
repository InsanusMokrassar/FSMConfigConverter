package com.github.insanusmokrassar.FSMConfigConverter

import com.github.insanusmokrassar.FSM.core.SimpleState
import com.github.insanusmokrassar.FSM.core.State
import com.github.insanusmokrassar.FSM.core.fromConfig
import com.github.insanusmokrassar.IObjectKRealisations.JSONIObject
import java.util.regex.PatternSyntaxException

val ruleRegex: Regex = Regex("^<((\\\\>)|[^>])+>")
val openRuleRegex: Regex = Regex("^<")
val closeRuleRegex: Regex = Regex(">$")

val multiChoiceRegex = Regex("\\[[^]]+]")
val nonMultiChoiceRegex = Regex("\\\\\\[")

val partsDelimiterRegex: Regex = Regex("^::=")

val rowsDelimiter: Regex = Regex("^[\n\r]+")

private fun String.startsWith(regex: Regex): Boolean {
    return regex.find(this) ?. let {
        startsWith(it.value)
    } ?: false
}

val preCheck = fromConfig(
        JSONIObject(PreviewState::class.java.classLoader.getResourceAsStream("scheme.json"))
)

fun compileFromConfig(config: String): List<PreviewState> {
    preCheck(config)
    var previousRowRoot: PreviewState? = null
    var currentRowRoot: PreviewState? = null
    var mutableConfig = config
    var wasDelimiter = false
    while (mutableConfig.isNotEmpty()) {
        val state = PreviewState()
        if (mutableConfig.startsWith(ruleRegex)) {
            val partWithRule = ruleRegex.find(mutableConfig)!!.groupValues.first()
            mutableConfig = mutableConfig.replaceFirst(ruleRegex, "")
            state.name = partWithRule
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
                    state.name = "\\z"
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
            state.name = try {
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

data class PreviewState internal constructor(
        var isRule: Boolean = true,
        var name: String? = null,
        var number: Int? = null) {

    private var toLeftInRow: PreviewState? = null

    val nextState: PreviewState?
        get() {
            return if (isRule && (toRightInRow == null || isStack)) {
                this.declarations.first()
            } else {
                toRightInRow
            }
        }

    var toRightInRow: PreviewState? = null
        private set

    private var toTop: PreviewState? = null

    private val top: PreviewState
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
                        toRightInRow !!. regex
                    } else {
                        var resultField = ""
                        declarations.forEach {
                            resultField += "|${it.regex}"
                        }
                        resultField.substring(1)
                    }
                } else {
                    if (name == "\\z") {
                        definition.usages.getRegexToRight()
                    } else {
                        name ?: ""
                    }
                }
                field = "(${Regex(field).pattern})"
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

    private fun List<PreviewState>.getRegexToRight(wasIn: MutableList<PreviewState> = ArrayList()): String {
        val regexBuilder = StringBuilder()
        forEach {
            if (!wasIn.contains(it)) {
                wasIn.add(it)
                it.toRightInRow ?. let {
                    regexBuilder.append(it.regex)
                } ?: {
                    regexBuilder.append(
                            it.definition.usages.getRegexToRight(wasIn)
                    )
                }()
            }
        }
        return Regex(regexBuilder.toString()).pattern
    }

    val isDefinition: Boolean
        get() = isRule && toLeftInRow == null

    private val definition: PreviewState
        get() = if (isDefinition) {
            this
        } else {
            toLeftInRow ?. definition ?: this
        }

    private val declarations: List<PreviewState>
        get() {
            val root = definition.top
            var current: PreviewState? = root
            val definitions = ArrayList<PreviewState>()
            while (current != null) {
                current = if (current.isDefinition) {
                    if (current.name == name) {
                        definitions.add(current)
                    }
                    current.lower
                } else {
                    null
                }
            }
            return definitions
        }

    private val usages: List<PreviewState>
        get() {
            return toList().filter {
                !it.isDefinition && it.isRule && this.name == it.name
            }
        }

    private val isFinal: Boolean
        get() = nextState == null && !isReturn

    val isAccept: Boolean
        get() = !isRule && (name != "\\z" || isFinal)

    val isError: Boolean
        get() = !isDefinition || lower ?.let {
            !it.isDefinition || it.name != name
        } ?: true

    val isReturn: Boolean
        get() = !isStack
                && !isRule
                && nextState == null
                && definition.usages.firstOrNull {
                    it.isStack
                } != null

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
                " $name"
        )
    }

    fun toString(isNumeric: Boolean = false): String {
        val pretext = if (isRule) {
            "<$name>"
        } else {
            name ?: ""
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

    val toFSMState: State?
        get() {
            return SimpleState(
                    isAccept,
                    isError,
                    isStack,
                    if (regex.isEmpty()) {
                        Regex("\\z")
                    } else {
                        Regex(regex)
                    },
                    nextState?.number
                )
        }
}
