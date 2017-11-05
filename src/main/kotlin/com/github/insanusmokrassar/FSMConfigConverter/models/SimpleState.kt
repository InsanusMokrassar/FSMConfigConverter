package com.github.insanusmokrassar.FSMConfigConverter.models

class SimpleState(
        private val realRegex: Regex,
        declaration: Declaration
) : CommonState(
        declaration
) {
    override val regex: Regex
        get() {
            return if (isEpsilon) {
                var regex = calculateRegexForEpsilon() ?. clearRegex() ?: ""
                if (regex.isEmpty()) {
                    regex = emptyRegex.pattern
                }
                Regex(regex)
            } else {
                realRegex
            }
        }

    override val isReturn: Boolean
        get() = toRightInRow == null && canBeInStack()
    override val accept: Boolean
        get() = !(isEpsilon && isReturn)
    override val error: Boolean = true
    override val next: Int?
        get() = toRightInRow ?. number
    override val stack: Boolean = false

    val isEpsilon: Boolean
        get() = realRegex.pattern == emptyRegex.pattern
                && toRightInRow == null
                && toLeftInRow == null
}
