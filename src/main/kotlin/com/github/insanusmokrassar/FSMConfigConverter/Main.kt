package com.github.insanusmokrassar.FSMConfigConverter

import java.io.File
import java.util.*
import java.util.logging.LogManager
import kotlin.collections.ArrayList

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
    val descriptor = FSMRulesDescriptor(config)
    println(descriptor.markdownContent)
    println(descriptor.statesConfig)
}

private val enableLogging = {
    _: Array<String> ->
    try {
        FSMRulesDescriptor::class.java.classLoader.getResourceAsStream("logging.properties").use {
            LogManager.getLogManager().readConfiguration(it)
        }
    } catch (e: Exception) {
        println("Can't load logger preferences: {$e}")
    }
}

private val fileMode = {
    args: Array<String> ->
    if (args.isEmpty()) {
        interactiveMode(args)
    } else {
        val inputFile = File(args[0])
        val config = inputFile.readText()
        val descriptor = FSMRulesDescriptor(config)

        val tempFile = File(inputFile.parent, "temp_${inputFile.nameWithoutExtension}.md")
        if (tempFile.exists()) {
            tempFile.delete()
        }
        tempFile.createNewFile()
        tempFile.appendText(descriptor.markdownContent)

        val outputFile = File(inputFile.parent, "out_${inputFile.nameWithoutExtension}.json")
        if (outputFile.exists()) {
            outputFile.delete()
        }
        outputFile.createNewFile()
        outputFile.appendText(descriptor.statesConfig)
    }
}

private val commands = mapOf(
        Pair(
                "-f",
                fileMode
        ),
        Pair(
                "--file",
                fileMode
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
                "-v",
                enableLogging
        ),
        Pair(
                "--verbose",
                enableLogging
        ),
        Pair(
                "--help",
                showHelp
        )
)

fun main(args: Array<String>) {
    val argsList = mutableListOf(*args)
    while (argsList.isNotEmpty()) {
        val commandArgs = getNextCommandWithArgs(argsList)
        commands[commandArgs[0]] ?. invoke(commandArgs)
    }
}

private val commandRegex = Regex("^--?.*")
fun getNextCommandWithArgs(args: MutableList<String>): Array<String> {
    val commandArgs = ArrayList<String>()

    while (commandArgs.isEmpty() && args.isNotEmpty()) {
        val current = args.removeAt(0)
        if (commandRegex.matches(current)) {
            commandArgs.add(current)
        }
    }

    while (args.isNotEmpty() && !commandRegex.matches(args[0])) {
        commandArgs.add(args.removeAt(0))
    }

    return commandArgs.toTypedArray()
}

fun getHelp(): String {
    return "Usage:\n'cmd <input file path>'\n" +
            "'-i' or '--interactive' for use interactive mod\n" +
            "'-f' or '--file' for read from .scheme file and out into temp_*.md and out_*.json files\n" +
            "'-v' or '--verbose' for enabling verbose mode\n"
}
