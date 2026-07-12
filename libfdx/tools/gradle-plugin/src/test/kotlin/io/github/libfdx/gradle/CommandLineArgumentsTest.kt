package io.github.libfdx.gradle

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CommandLineArgumentsTest {
    @Test
    fun parsesBasicArguments() {
        assertEquals(listOf("triangle", "3"), parseCommandLineArguments("  triangle   3  "))
    }

    @Test
    fun preservesQuotedAndEmptyArguments() {
        assertEquals(
            listOf("triangle", "3", "--capture=build/reports/a b.ppm", "--label=hello world", ""),
            parseCommandLineArguments(
                "triangle 3 \"--capture=build/reports/a b.ppm\" '--label=hello world' \"\""
            )
        )
    }

    @Test
    fun preservesWindowsPathsAndEscapedWhitespace() {
        assertEquals(
            listOf(
                "--capture=C:\\Program Files\\libfdx\\frame.ppm",
                "--root=C:\\dev\\libfdx",
                "escaped value"
            ),
            parseCommandLineArguments(
                "\"--capture=C:\\Program Files\\libfdx\\frame.ppm\" " +
                    "--root=C:\\dev\\libfdx escaped\\ value"
            )
        )
    }

    @Test
    fun parsesEscapedQuotesAndAdjacentSegments() {
        assertEquals(
            listOf("--title=\"quoted\"", "prefix middle suffix"),
            parseCommandLineArguments("--title=\\\"quoted\\\" prefix\" middle \"suffix")
        )
    }

    @Test
    fun rejectsUnterminatedQuotes() {
        assertThrows(GradleException::class.java) {
            parseCommandLineArguments("triangle \"unterminated")
        }
    }
}
