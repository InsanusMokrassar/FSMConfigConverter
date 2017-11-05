package com.github.insanusmokrassar.FSMConfigConverter

import com.github.insanusmokrassar.FSM.core.StateAction
import com.github.insanusmokrassar.FSMConfigConverter.models.emptyRegex
import com.github.insanusmokrassar.IObjectK.interfaces.IObject
import com.github.insanusmokrassar.IObjectK.interfaces.has
import com.github.insanusmokrassar.IObjectK.realisations.SimpleIObject
import java.util.logging.Logger
import java.util.regex.PatternSyntaxException

val lastDeclarationField = "lastDeclaration"
private val nameField = "name"
private val regexField = "regex"

val numbered = "numbered"
val unNumbered = "unNumbered"


private fun IObject<Any>.getOrDefaultAndRemove(field: String, default: String = ""): String {
    return if (has(field)) {
        val fieldValue = get<String>(field)
        remove(field)
        fieldValue
    } else {
        default
    }
}

class ReadInField(private val field: String): StateAction {
    override fun invoke(scope: IObject<Any>, input: String) {
        val oldValue = scope.getOrDefaultAndRemove(field)
        Logger.getGlobal().info("Read: \"$oldValue$input\"")
        scope.put(
                field,
                "$oldValue$input"
        )
    }
}

class DeclarationCreator: StateAction {
    override fun invoke(scope: IObject<Any>, input: String) {
        val declaration = Declaration(
                scope.getOrDefaultAndRemove(nameField),
                if (scope.has(lastDeclarationField)) {
                    scope.get<Declaration>(lastDeclarationField)
                } else {
                    null
                }
        )
        declaration.previousDeclaration ?. nextDeclaration = declaration
        scope.put(lastDeclarationField, declaration)
    }
}

class RuleCreator: StateAction {
    override fun invoke(scope: IObject<Any>, input: String) {
        if (scope.has(lastDeclarationField)) {
            scope.get<Declaration>(lastDeclarationField).addRule(
                    scope.getOrDefaultAndRemove(nameField)
            )
        } else {
            throw IllegalStateException("Must have precreated declaration")
        }
    }
}

class StateCreator: StateAction {
    override fun invoke(scope: IObject<Any>, input: String) {
        if (scope.has(lastDeclarationField)) {
            val field = scope.getOrDefaultAndRemove(regexField)
            scope.get<Declaration>(lastDeclarationField).addSimpleState(
                    try {
                        Regex(field)
                    } catch (e: PatternSyntaxException) {
                        Regex("\\$field")
                    }
            )
        } else {
            throw IllegalStateException("Must have precreated declaration")
        }
    }
}

class EmptyStateCreator: StateAction {
    override fun invoke(scope: IObject<Any>, input: String) {
        if (scope.has(lastDeclarationField)) {
            scope.get<Declaration>(lastDeclarationField).addSimpleState(
                    emptyRegex
            )
        } else {
            throw IllegalStateException("Must have precreated declaration")
        }
    }
}

class ExpressionComplete(
        config: IObject<Any>
): StateAction {

    private val numberedField: Boolean = if (config.has(numbered)) {
        config.get(numbered)
    } else {
        true
    }
    private val unNumberedField: Boolean = if (config.has(unNumbered)) {
        config.get(unNumbered)
    } else {
        true
    }

    constructor(): this(SimpleIObject())

    override fun invoke(scope: IObject<Any>, input: String) {
        val declaration = scope.get<Declaration>(lastDeclarationField)
        if (unNumberedField) {
            scope.put(unNumbered, createRuleScheme(declaration, false))
        }
        if (numberedField) {
            scope.put(numbered, createRuleScheme(declaration, true))
        }
    }

    private fun createRuleScheme(declaration: Declaration, isNumbered: Boolean = false): String {
        val builder = StringBuilder()
        declaration.declarationsList().forEach {
            builder.append(
                    "${if (isNumbered) it.number.toString() else ""}<${it.name}>::="
            )
            var state = it.declarationStartState
            while (state != null) {
                if (isNumbered) {
                    builder.append(state.number)
                }
                when(state) {
                    is Rule -> builder.append("<${state.name}>")
                    is SimpleState -> builder.append(state.regex.pattern)
                }
                state = state.toRightInRow
            }
            builder.append("\n")
        }
        return builder.toString()
    }
}
