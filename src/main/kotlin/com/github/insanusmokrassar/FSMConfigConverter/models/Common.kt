package com.github.insanusmokrassar.FSMConfigConverter.models

val emptyRegex = Regex("^$")
val redundantOrRegex = Regex("\\|{2,}")
val redundantRegex = Regex("\\(\\|*\\)")
val redundantOrInTheEndRegex = Regex("\\|+\\)")

internal fun String.clearRegex(): String {
    var regex = this
    while (redundantRegex.find(regex) != null) {
        regex = redundantRegex.replace(regex, "")
        if (redundantOrRegex.find(regex) != null) {
            regex = redundantOrRegex.replace(regex, "|")
        }
        if (redundantOrInTheEndRegex.find(regex) != null) {
            regex = redundantOrInTheEndRegex.replace(regex, ")")
        }
    }
    return regex
}
