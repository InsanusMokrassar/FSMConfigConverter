package com.github.insanusmokrassar.FSMConfigConverter

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

val redundantGroup: Regex = Regex("^\\([^)]+\\)$")
val redundantGroupStart: Regex = Regex("^\\(")
val redundantGroupEnd: Regex = Regex("\\)$")

private fun String.startsWith(regex: Regex): Boolean {
    return regex.find(this) ?. let {
        startsWith(it.value)
    } ?: false
}

private fun String.removeRedundantGroups(): String {
    var result = this
    while (redundantGroup.matches(result)) {
        result = redundantGroupStart.replace(result, "")
        result = redundantGroupEnd.replace(result, "")
    }
    return result
}

val preCheck = fromConfig(
        JSONIObject(PreviewState::class.java.classLoader.getResourceAsStream("scheme.json"))
)

@Throws(IllegalArgumentException::class)
fun compileFromConfig(input: String): List<PreviewState> {
    val config = if (input.last() == '\n') {
        input
    } else {
        "$input\n"
    }
    try {
        preCheck(config)
    } catch (e: Exception) {
        throw IllegalArgumentException("You have problems with config", e)
    }
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
            state.name = if (mutableConfig.startsWith(multiChoiceRegex)) {
                val resultRegex = Regex(multiChoiceRegex.find(mutableConfig)?.value ?: "")
                mutableConfig = mutableConfig.replaceFirst(resultRegex.pattern, "")
                resultRegex.pattern
            } else {
                if (mutableConfig.startsWith(nonMultiChoiceRegex)) {
                    val text = nonMultiChoiceRegex.find(mutableConfig)!!.value
                    mutableConfig = mutableConfig.replaceFirst(text, "")
                    text
                } else {
                    var text = "${mutableConfig.first()}"
                    mutableConfig = mutableConfig.replaceFirst(text, "")
                    text = try {
                        Regex("[$text]").pattern
                    } catch (e: PatternSyntaxException) {
                        if (text.isEmpty()) {
                            Regex("()").pattern
                        } else {
                            Regex("\\" + text).pattern
                        }
                    }
                    text
                }
            }
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

    private var regexInited = false
    var regex: String = ""
        get() {
            return if (regexInited) {
                field
            } else {
                regexInited = true
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
                field = "(${Regex(field.removeRedundantGroups()).pattern})"
                field
            }
        }
        set(value) {
            if (isRule) {
                field = regex
                regexInited = true
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

//    val toFSMState: State?
//        get() {
//            return SimpleState(
//                    isAccept,
//                    isError,
//                    isStack,
//                    if (regex.isEmpty()) {
//                        Regex("\\z")
//                    } else {
//                        Regex(regex)
//                    },
//                    nextState?.number
//                )
//        }
}

//---------------------------------------------------------------------------

class Declaration(
        val name: String,
        val previousDeclaration: Declaration? = null
): State {

    var nextDeclaration: Declaration? = null
    var declarationStartState: CommonState = SimpleState(this, Regex("^$"))

    val number: Int
        get() = previousDeclaration ?. let { number + 1 } ?: 0

    override val accept: Boolean = false
    override val error: Boolean
        get() = nextDeclaration != null && nextDeclaration?. name == name
    override val next: Int? = declarationStartState.number
    override val regex: Regex = declarationStartState.regex
    override val stack: Boolean = false
    override val action: (String) -> Unit = {}
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
        it.declarationStartState.toStatesList().filter{
            it is Rule && it.name == name
        }.mapTo(
                result,
                { it as Rule }
        )
    }
    return result
}

open class Rule(
        val name: String,
        declaration: Declaration
) : CommonState(declaration) {
    override var toRightInRow: CommonState? = null
    override val accept: Boolean = false
    override val error: Boolean = true
    override val next: Int? = findDeclarations().first().number
    override val regex: Regex
        get() {
            return Regex(
                    findDeclarations().joinToString("|", "(", ")") {
                        "(${it.regex.pattern})"
                    }
            )
        }
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
        declaration: Declaration,
        private val realRegex: Regex
) : CommonState(
        declaration
) {
    override val regex: Regex
        get() {
            return if (isEpsilon()) {
                Regex(calculateRegexForEpsilon() ?: "")
            } else {
                realRegex
            }
        }

    override var toRightInRow: CommonState? = null
    override val isReturn: Boolean
        get() = toRightInRow == null && canBeInStack()
    override val accept: Boolean
        get() = !(toLeftInRow == null && toRightInRow == null && !isReturn)
    override val error: Boolean = true
    override val next: Int?
        get() = toRightInRow ?. number
    override val stack: Boolean = false
}

fun SimpleState.isEpsilon(): Boolean = toRightInRow == null && toLeftInRow == null

abstract class CommonState(
        val declaration: Declaration
): State {
    val toLeftInRow: CommonState?
        get () {
            return if (declaration.declarationStartState == this) {
                null
            } else {
                var current = declaration.declarationStartState
                while (current.toRightInRow != this) {
                    current = current.toRightInRow ?: throw IllegalStateException(
                            "This state is not in rule list of declaration. Check declaration setting"
                    )
                }
                current
            }
        }
    val number: Int
        get() {
            return toLeftInRow ?.let { number + 1 } ?: if (declaration.previousDeclaration == null) {
                declaration.declarationsList().last().number + 1
            } else {
                declaration.previousDeclaration.declarationStartState.toStatesList().last().number + 1
            }
        }
    abstract var toRightInRow: CommonState?
    abstract val isReturn: Boolean
    override val action: (String) -> Unit = {}
}

fun CommonState.canBeInStack(): Boolean = declaration.usages().firstOrNull { it.stack } != null

fun CommonState.toStatesList(): List<CommonState> {
    var current: CommonState? = declaration.declarationStartState
    val result = ArrayList<CommonState>()
    while (current != null) {
        result.add(current)
        current = current.toRightInRow
    }
    return result
}

fun Rule.calculateRegexForEpsilon(
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
