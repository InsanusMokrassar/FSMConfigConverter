package com.github.insanusmokrassar.FSMConfigConverter.actions.creators

import com.github.insanusmokrassar.FSM.core.StateAction
import com.github.insanusmokrassar.FSMConfigConverter.actions.getOrDefaultAndRemove
import com.github.insanusmokrassar.FSMConfigConverter.actions.lastDeclarationField
import com.github.insanusmokrassar.FSMConfigConverter.models.Declaration
import com.github.insanusmokrassar.FSMConfigConverter.models.emptyRegex
import com.github.insanusmokrassar.FSMConfigConverter.actions.regexField
import com.github.insanusmokrassar.IObjectK.interfaces.IObject
import com.github.insanusmokrassar.IObjectK.interfaces.has
import java.util.regex.PatternSyntaxException


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
