package com.github.insanusmokrassar.FSMConfigConverter.models

open class Rule(
        val name: String,
        declaration: Declaration
) : CommonState(declaration) {
    override val accept: Boolean = false
    override val error: Boolean = true
    override val next: Int?
        get() = findDeclarations().first().number
    override val regex: Regex
        get() = Regex(
                findDeclarations().joinToString("|", "(", ")") {
                    "(${it.regex.pattern})"
                }.clearRegex()
        )
    override val stack: Boolean
        get() = toRightInRow != null
    override val isReturn: Boolean = false
}

fun Rule.findDeclarations(): List<Declaration> {
    var current: Declaration? = declaration.root()
    val result = ArrayList<Declaration>()
    while (current != null) {
        if (current.name == name) {
            result.add(current)
        }
        current = current.nextDeclaration
    }
    return result
}
