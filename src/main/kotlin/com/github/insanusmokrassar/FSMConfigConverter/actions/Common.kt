package com.github.insanusmokrassar.FSMConfigConverter.actions

import com.github.insanusmokrassar.IObjectK.interfaces.IObject
import com.github.insanusmokrassar.IObjectK.interfaces.has

val lastDeclarationField = "lastDeclaration"
internal val nameField = "name"
internal val regexField = "regex"

val numbered = "numbered"
val unNumbered = "unNumbered"


internal fun IObject<Any>.getOrDefaultAndRemove(field: String, default: String = ""): String {
    return if (has(field)) {
        val fieldValue = get<String>(field)
        remove(field)
        fieldValue
    } else {
        default
    }
}
