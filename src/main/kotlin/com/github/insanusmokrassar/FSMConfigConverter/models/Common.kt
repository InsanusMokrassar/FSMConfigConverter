package com.github.insanusmokrassar.FSMConfigConverter.models

import java.util.logging.Logger
import java.util.regex.PatternSyntaxException

val emptyRegex = Regex("^$")
val redundantOrRegex = Regex("\\|{2,}")
val redundantRegex = Regex("\\(\\|+\\)")
val redundantOrInTheEndRegex = Regex("\\|+\\)")
val redundantOrInTheBeginningRegex = Regex("\\(\\|+")

val groupsRegex = Regex("\\(((\\\\\\()|[^()])*\\)")
val groupBeginRegex = Regex("^\\(")
val groupEndRegex = Regex("\\)$")

val variantsRegex = Regex("\\[((\\\\\\[)|[^\\[)\\]])*\\]")
val variantsStartRegex = Regex("^\\[")
val variantsEndRegex = Regex("\\]$")

internal fun String.clearRegex(): String {
    var regex = this
    val containsEnd = contains(emptyRegex.pattern)
    if (containsEnd) {
        regex = replace(emptyRegex.pattern, "")
    }
    while (redundantRegex.find(regex) != null) {
        regex = redundantRegex.replace(regex, "")
        if (redundantOrRegex.find(regex) != null) {
            regex = redundantOrRegex.replace(regex, "|")
        }
        if (redundantOrInTheEndRegex.find(regex) != null) {
            regex = redundantOrInTheEndRegex.replace(regex, ")")
        }
        if (redundantOrInTheBeginningRegex.find(regex) != null) {
            regex = redundantOrInTheBeginningRegex.replace(regex, "(")
        }
    }
    val groups = HashSet<String>()
    groupsRegex.findAll(regex).map { it.groupValues.first() }.filter {
        it.isNotEmpty() && it != "()"
    }.map {
        if (groupsRegex.find(it) != null) {
            var currentRegex = groupEndRegex.replaceFirst(
                    groupBeginRegex.replaceFirst(it, ""),
                    ""
            )
            try {
                Regex("[$currentRegex]")
                currentRegex
            } catch (e: PatternSyntaxException) {
                currentRegex = "\\$currentRegex"
                Regex("[$currentRegex]")
                currentRegex
            }
        } else {
            it
        }
    }.map {
        if (variantsRegex.find(it) != null) {
            variantsEndRegex.replaceFirst(
                    variantsStartRegex.replaceFirst(
                            it,
                            ""
                    ),
                    ""
            )
        } else {
            it
        }
    }.forEach {
        groups.add(it)
    }/*.forEach {
            groups.addAll(
                    it.filter {
                        it.isNotEmpty()
                    }.map {
                        if (it == "]") {
                            "\\]"
                        } else {
                            try {
                                Regex("[$it]")
                                it
                            } catch (e: PatternSyntaxException) {
                                Regex("[\\$it]").pattern
                                "\\$it"
                            }
                        }
                    }
            )
        }*/
    var group = groups.joinToString("", "[", "]")
    if (containsEnd && groups.isNotEmpty()) {
        group = "(($group)|(${emptyRegex.pattern}))"
    }
    Logger.getGlobal().info(
            "Groups of regex:\n$group"
    )
    return try {
        Regex(group).pattern
    } catch (e: PatternSyntaxException) {
        if (groups.isEmpty()) {
            emptyRegex.pattern
        } else {
            throw e
        }
    }
}
