package io.github.libfdx.gradle

import org.gradle.api.GradleException

internal fun parseCommandLineArguments(commandLine: String): List<String> {
    val arguments = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var tokenStarted = false
    var index = 0
    while(index < commandLine.length) {
        val character = commandLine[index]
        val activeQuote = quote
        if(activeQuote == null) {
            when {
                character.isWhitespace() -> {
                    if(tokenStarted) {
                        arguments.add(current.toString())
                        current.setLength(0)
                        tokenStarted = false
                    }
                }
                character == '\'' || character == '"' -> {
                    quote = character
                    tokenStarted = true
                }
                character == '\\' && index + 1 < commandLine.length
                        && isEscapableOutsideQuotes(commandLine[index + 1]) -> {
                    index++
                    current.append(commandLine[index])
                    tokenStarted = true
                }
                else -> {
                    current.append(character)
                    tokenStarted = true
                }
            }
        }
        else if(character == activeQuote) {
            quote = null
        }
        else if(activeQuote == '"' && character == '\\' && index + 1 < commandLine.length
                && isEscapableInsideDoubleQuotes(commandLine[index + 1])) {
            index++
            current.append(commandLine[index])
            tokenStarted = true
        }
        else {
            current.append(character)
            tokenStarted = true
        }
        index++
    }
    if(quote != null) {
        throw GradleException("Unterminated quote in libfdx.desktopC.runArgs")
    }
    if(tokenStarted) {
        arguments.add(current.toString())
    }
    return arguments
}

private fun isEscapableOutsideQuotes(character: Char): Boolean {
    return character.isWhitespace() || character == '\'' || character == '"' || character == '\\'
}

private fun isEscapableInsideDoubleQuotes(character: Char): Boolean {
    return character == '"' || character == '\\'
}
