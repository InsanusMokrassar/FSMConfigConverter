package com.github.insanusmokrassar.FSMConfigConverter.actions

import com.github.insanusmokrassar.FSM.core.SimpleState
import com.github.insanusmokrassar.FSM.core.StateAction
import com.github.insanusmokrassar.FSMConfigConverter.models.Declaration
import com.github.insanusmokrassar.FSMConfigConverter.models.Rule
import com.github.insanusmokrassar.FSMConfigConverter.models.declarationsList
import com.github.insanusmokrassar.IObjectK.interfaces.IObject
import com.github.insanusmokrassar.IObjectK.interfaces.has
import com.github.insanusmokrassar.IObjectK.realisations.SimpleIObject


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
