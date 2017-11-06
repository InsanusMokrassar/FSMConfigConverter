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

val afterVariantsRegex = Regex("([^\\\\]|[\\\\].)")

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
        if (redundantOrInTheBeginningRegex.find(regex) != null) {
            regex = redundantOrInTheBeginningRegex.replace(regex, "(")
        }
    }
    val groups = HashSet<String>()
    groupsRegex.findAll(regex).map { it.groupValues }.forEach {
        it.filter {
            it.isNotEmpty()
        }.map {
            if (groupsRegex.find(it) != null) {
                groupEndRegex.replaceFirst(
                        groupBeginRegex.replaceFirst(it, ""),
                        ""
                )
            } else {
                it
            }
        }.map {
            if (variantsRegex.find(it) != null) {
                val variants = HashSet<String>()
                afterVariantsRegex.findAll(
                        variantsEndRegex.replaceFirst(
                            variantsStartRegex.replaceFirst(
                                    it,
                                    ""
                            ),
                            ""
                        )
                ).forEach {
                    variants.addAll(it.groupValues)
                }
                variants.toTypedArray()
            } else {
                arrayOf(it)
            }
        }.forEach {
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
        }
    }
    val group = groups.joinToString("", "[", "]")
    Logger.getGlobal().info(
            "Groups of regex:\n$group"
    )
    return Regex(group).pattern
}
