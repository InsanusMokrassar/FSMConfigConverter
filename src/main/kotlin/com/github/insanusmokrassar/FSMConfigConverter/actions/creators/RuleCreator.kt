package com.github.insanusmokrassar.FSMConfigConverter.actions.creators

import com.github.insanusmokrassar.FSM.core.StateAction
import com.github.insanusmokrassar.FSMConfigConverter.actions.getOrDefaultAndRemove
import com.github.insanusmokrassar.FSMConfigConverter.actions.lastDeclarationField
import com.github.insanusmokrassar.FSMConfigConverter.actions.nameField
import com.github.insanusmokrassar.FSMConfigConverter.models.Declaration
import com.github.insanusmokrassar.IObjectK.interfaces.IObject
import com.github.insanusmokrassar.IObjectK.interfaces.has

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
