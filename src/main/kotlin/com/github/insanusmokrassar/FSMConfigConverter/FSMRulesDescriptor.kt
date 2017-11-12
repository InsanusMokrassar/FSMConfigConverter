package com.github.insanusmokrassar.FSMConfigConverter

import com.github.insanusmokrassar.FSM.core.State
import com.github.insanusmokrassar.FSM.extensions.createRunnerFromConfig
import com.github.insanusmokrassar.FSM.extensions.toConfigObject
import com.github.insanusmokrassar.FSMConfigConverter.actions.lastDeclarationField
import com.github.insanusmokrassar.FSMConfigConverter.actions.numbered
import com.github.insanusmokrassar.FSMConfigConverter.actions.unNumbered
import com.github.insanusmokrassar.FSMConfigConverter.models.Declaration
import com.github.insanusmokrassar.FSMConfigConverter.models.root
import com.github.insanusmokrassar.FSMConfigConverter.models.toStatesList
import com.github.insanusmokrassar.IObjectK.interfaces.has
import com.github.insanusmokrassar.IObjectK.realisations.SimpleIObject
import com.github.insanusmokrassar.IObjectKRealisations.JSONIObject

private val preCheck = createRunnerFromConfig(
        JSONIObject(Declaration::class.java.classLoader.getResourceAsStream("scheme.json"))
)

class FSMRulesDescriptor(input: String) {
    private val rootDeclaration: Declaration
    val states: List<State>
    val numberedRules: String?
    val unNumberedRules: String?

    init {
        val config = if (input.last() == '\n') {
            input
        } else {
            "$input\n"
        }
        try {
            val compiled = SimpleIObject()
            preCheck(compiled, config)
            rootDeclaration = compiled.get<Declaration>(lastDeclarationField).root()
            states = rootDeclaration.toStatesList()
            numberedRules = if (compiled.has(numbered)) {
                compiled.get(numbered)
            } else {
                null
            }
            unNumberedRules = if (compiled.has(unNumbered)) {
                compiled.get(unNumbered)
            } else {
                null
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("You have problems with config", e)
        }
    }

    val markdownContent: String
        get() {
            val builder = StringBuilder()
            builder.append("```\n\n$unNumberedRules\n\n$numberedRules\n\n```\n\n")
            builder.append("| N | Accept | Error | Return | Stack | Next | Regex |\n")
            builder.append("|---|--------|-------|--------|-------|------|-------|\n")
            states.forEachIndexed {
                index, it ->
                builder.append("| $index | ${it.accept} | ${it.error} | ${it.next == null} | ${it.stack} " +
                        "| ${if (it.next == null) "-" else it.next.toString()} | ${it.regex} |\n")
            }
            return builder.toString()
        }

    val statesConfig: String
        get() {
            val configBuilder = StringBuilder()
            configBuilder.append("[")
            states.forEach {
                configBuilder.append(
                        "${it.toConfigObject(states.indexOf(it).toString())},"
                )
            }
            return configBuilder.toString().replace(Regex(",$"), "]\n")
        }
}
