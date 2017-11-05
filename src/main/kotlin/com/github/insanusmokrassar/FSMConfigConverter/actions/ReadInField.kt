package com.github.insanusmokrassar.FSMConfigConverter.actions

import com.github.insanusmokrassar.FSM.core.StateAction
import com.github.insanusmokrassar.IObjectK.interfaces.IObject
import java.util.logging.Logger


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
