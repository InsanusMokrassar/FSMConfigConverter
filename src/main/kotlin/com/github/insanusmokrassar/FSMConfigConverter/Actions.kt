package com.github.insanusmokrassar.FSMConfigConverter

import com.github.insanusmokrassar.FSM.core.StateAction
import com.github.insanusmokrassar.IObjectK.interfaces.IObject
import com.github.insanusmokrassar.IObjectK.interfaces.has

val lastDeclarationField = "lastDeclaration"
private val nameField = "name"
private val regexField = "name"


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
        scope.put(
                field,
                "${scope.getOrDefaultAndRemove(field)}$input"
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
            scope.get<Declaration>(lastDeclarationField).addSimpleState(
                    Regex(scope.getOrDefaultAndRemove(regexField))
            )
        } else {
            throw IllegalStateException("Must have precreated declaration")
        }
    }
}


