package com.github.insanusmokrassar.FSMConfigConverter

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

fun fromConfig(config: String): List<PreState> {
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
        if (preStates.lastIndex != it.number) {
            it.nextState = preStates[it.number!! + 1]
        }
    }
    return preStates
}

data class PreState internal constructor(
        var isRule: Boolean = true,
        var text: String? = null,
        var number: Int? = null,
        var nextState: PreState? = null) {

    var toRightInRow: PreState? = null
        private set
    private var toLeftInRow: PreState? = null

    val isDefinition: Boolean
        get() = isRule && toLeftInRow == null

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


}
