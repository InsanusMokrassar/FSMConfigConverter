package com.github.insanusmokrassar.FSMConfigConverter.actions.creators

import com.github.insanusmokrassar.FSM.core.StateAction
import com.github.insanusmokrassar.FSMConfigConverter.actions.getOrDefaultAndRemove
import com.github.insanusmokrassar.FSMConfigConverter.actions.lastDeclarationField
import com.github.insanusmokrassar.FSMConfigConverter.actions.nameField
import com.github.insanusmokrassar.FSMConfigConverter.models.Declaration
import com.github.insanusmokrassar.IObjectK.interfaces.IObject
import com.github.insanusmokrassar.IObjectK.interfaces.has

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
