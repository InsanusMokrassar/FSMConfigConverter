package com.github.insanusmokrassar.FSMConfigConverter

import java.io.File
import java.util.*
import java.util.logging.LogManager

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
        FSMRulesDescriptor::class.java.classLoader.getResourceAsStream("logging.properties").use {
            LogManager.getLogManager().readConfiguration(it)
        }
    } catch (e: Exception) {
        println("Can't load logger preferences: {$e}")
    }
    if (args.isEmpty()) {
        commands[null]!!(args)
    } else {
        commands[args[0]]!!(args)
    }
}

fun getHelp(): String {
    return "Usage:\n'cmd <input file path>'\n'-i' or '--interactive' for use interactive mod"
}
