package com.github.insanusmokrassar.FSMConfigConverter

import com.github.insanusmokrassar.FSM.core.State
import com.github.insanusmokrassar.FSM.core.toConfigString
import com.github.insanusmokrassar.FSMConfigConverter.actions.lastDeclarationField
import com.github.insanusmokrassar.FSMConfigConverter.actions.numbered
import com.github.insanusmokrassar.FSMConfigConverter.actions.unNumbered
import com.github.insanusmokrassar.FSMConfigConverter.models.Declaration
import com.github.insanusmokrassar.FSMConfigConverter.models.toStatesList
import com.github.insanusmokrassar.IObjectK.interfaces.IObject
import java.io.File
import java.util.*

private val showHelp = {
    _: Array<String> ->
    println(getHelp())
}

private val interactiveInputCompleteRegex = Regex("\n\n")

private val interactiveMode = {
    _: Array<String> ->
    println("Write your scheme. For completing insert double new line")
    var config = ""
    val scanner = Scanner(System.`in`)
    while (interactiveInputCompleteRegex.find(config) == null) {
        config += "${scanner.nextLine()}\n"
    }
    val compiled = compileFromConfig(config)
    println(getContent("Interactive", compiled))
    println(getConfig(compiled.get<Declaration>(lastDeclarationField).toStatesList()))
}

private val commands = mapOf(
        Pair(
                null,
                {
                    args ->
                    if (args.isEmpty()) {
                        interactiveMode(args)
                    } else {
                        val inputFile = File(args[0])
                        val config = inputFile.readText()
                        val calcConfig = compileFromConfig(config)
                        val states = calcConfig.get<Declaration>(lastDeclarationField).toStatesList()

                        val tempContent = getContent(inputFile.nameWithoutExtension, calcConfig)
                        val tempFile = File(inputFile.parent, "temp_${inputFile.nameWithoutExtension}.md")
                        if (tempFile.exists()) {
                            tempFile.delete()
                        }
                        tempFile.createNewFile()
                        tempFile.appendText(tempContent)

                        val outputFile = File(inputFile.parent, "out_${inputFile.nameWithoutExtension}.json")
                        if (outputFile.exists()) {
                            outputFile.delete()
                        }
                        outputFile.createNewFile()
                        outputFile.appendText(getConfig(states))
                    }
                }
        ),
        Pair(
                "-i",
                interactiveMode
        ),
        Pair(
                "--interactive",
                interactiveMode
        ),
        Pair(
                "--help",
                showHelp
        )
)

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        commands[null]!!(args)
    } else {
        commands[args[0]]!!(args)
    }
}

fun getHelp(): String {
    return "Usage:\n'cmd <input file path>'\n'-i' or '--interactive' for use interactive mod"
}

fun getContent(statesName: String, input: IObject<Any>): String {
    val states = input.get<Declaration>(lastDeclarationField).toStatesList()
    val numbered = input.get<String>(numbered)
    val unNumbered = input.get<String>(unNumbered)
    val builder = StringBuilder()
    builder.append("# $statesName\n\n")
    builder.append("```\n\n$numbered\n\n$unNumbered\n\n```\n\n")
    builder.append("| N | Accept | Error | Return | Stack | Next | Regex |\n")
    builder.append("|---|--------|-------|--------|-------|------|-------|\n")
    states.forEachIndexed {
        index, it ->
        builder.append("| ${index} | ${it.accept} | ${it.error} | ${it.next == null} | ${it.stack} " +
                "| ${if (it.next == null) "-" else it.next.toString()} | ${it.regex} |\n")
    }
    return builder.toString()
}

fun getConfig(states: List<State>): String {
    val configBuilder = StringBuilder()
    configBuilder.append("[")
    states.forEach {
        configBuilder.append(
                "${it.toConfigString()},"
        )
    }
    return configBuilder.toString().replace(Regex(",$"), "]\n")
}
