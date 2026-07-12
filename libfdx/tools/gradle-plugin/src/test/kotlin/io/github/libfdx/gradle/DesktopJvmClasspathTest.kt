package io.github.libfdx.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DesktopJvmClasspathTest {
    @Test
    fun targetRuntimePrecedesApplicationRuntime() {
        val project = ProjectBuilder.builder().build()
        val targetFile = project.file("target-provider.jar")
        val applicationFile = project.file("application-runtime.jar")

        val classpath = prioritizedDesktopJvmClasspath(
            project.files(targetFile),
            project.files(applicationFile)
        )

        assertEquals(listOf(targetFile, applicationFile), classpath.files.toList())
    }
}
