package com.github.insanusmokrassar.FSMConfigConverter

import com.github.insanusmokrassar.FSM.core.toConfigString
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
    val preStates = compileFromConfig(config)
    println(getContent("Interactive", preStates))
    println(getConfig(preStates))
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
                        val preStates = compileFromConfig(config)

                        val tempContent = getContent(inputFile.nameWithoutExtension, preStates)
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
                        outputFile.appendText(getConfig(preStates))
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
    try {
        commands[args[0]]!!(args)
    } catch (e: Exception){
        commands[null]!!(args)
    }
}

fun getHelp(): String {
    return "Usage:\n'cmd <input file path>'\n'-i' or '--interactive' for use interactive mod"
}

fun getContent(statesName: String, previewStates: List<PreviewState>): String {
    val builder = StringBuilder()
    builder.append("# $statesName\n\n")
    builder.append("```\n${getRules(false, previewStates)}\n\n${getRules(true, previewStates)}\n```\n\n")
    builder.append("| N | Accept | Error | Return | Stack | Next | Regex |\n")
    builder.append("|---|--------|-------|--------|-------|------|-------|\n")
    previewStates.forEach {
        builder.append("| ${it.number} | ${it.isAccept} | ${it.isError} | ${it.isReturn} | ${it.isStack} " +
                "| ${if (it.nextState == null) "-" else it.nextState!!.number.toString()} | ${it.regex} |\n")
    }
    return builder.toString()
}

fun getConfig(preStates: List<PreviewState>): String {
    val configBuilder = StringBuilder()
    configBuilder.append("[")
    preStates.forEach {
        configBuilder.append(
                "${it.toFSMState!!.toConfigString()},"
        )
    }
    return configBuilder.toString().replace(Regex(",$"), "]\n")
}

fun getRules(isNumeric: Boolean, previewStates: List<PreviewState>): String {
    val builder = StringBuilder()
    val definitions = previewStates.filter { it.isDefinition }
    definitions.forEach {
        var current: PreviewState? = it
        while (current != null) {
            builder.append(current.toString(isNumeric))
            if (current.isDefinition) {
                builder.append("::=")
            }
            current = current.toRightInRow
        }
        builder.append("\n")
    }
    return builder.toString()
}
