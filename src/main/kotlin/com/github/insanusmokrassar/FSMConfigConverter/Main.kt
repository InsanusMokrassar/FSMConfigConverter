package com.github.insanusmokrassar.FSMConfigConverter

import java.io.File

private val showHelp = {
    _: Array<String> ->
    println(getHelp())
}

private val commands = mapOf(
        Pair(
                null,
                {
                    args ->
                    if (args.isEmpty()) {
                        showHelp(args)
                    } else {
                        val config = File(args[0]).readText()
                        fromConfig(config)
                    }
                }
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
    return "Usage:\ncmd <input file path> <output file path> <support file path>"
}
