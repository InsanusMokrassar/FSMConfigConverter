package com.github.insanusmokrassar.FSMConfigConverter

import com.github.insanusmokrassar.FSM.core.fromConfig
import com.github.insanusmokrassar.FSMConfigConverter.models.Declaration
import com.github.insanusmokrassar.IObjectK.interfaces.IObject
import com.github.insanusmokrassar.IObjectK.realisations.SimpleIObject
import com.github.insanusmokrassar.IObjectKRealisations.JSONIObject

val preCheck = fromConfig(
        JSONIObject(Declaration::class.java.classLoader.getResourceAsStream("scheme.json"))
)

@Throws(IllegalArgumentException::class)
fun compileFromConfig(input: String): IObject<Any> {
    val config = if (input.last() == '\n') {
        input
    } else {
        "$input\n"
    }
    try {
        val result = SimpleIObject()
        preCheck(result, config)
        return result
    } catch (e: Exception) {
        throw IllegalArgumentException("You have problems with config", e)
    }
}
