package io.github.libfdx.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class DesktopCRunTaskTest {
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    fun `runs child with output exceeding pipe capacity and preserves arguments and working directory`() {
        val task = task(0)

        task.run()

        assertEquals("argument with spaces", Files.readString(task.releaseDir.get().asFile.toPath().resolve("result.txt")))
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    fun `reports child nonzero exit after draining output`() {
        val task = task(7)

        val failure = assertThrows(IllegalStateException::class.java) { task.run() }

        assertEquals("Native executable failed with exit code 7", failure.message)
        assertEquals("argument with spaces", Files.readString(task.releaseDir.get().asFile.toPath().resolve("result.txt")))
    }

    private fun task(exitCode: Int): LibfdxDesktopCRunTask {
        val temporaryRoot = Path.of("build/tmp/desktop-c-run").toAbsolutePath()
        Files.createDirectories(temporaryRoot)
        val root = Files.createTempDirectory(temporaryRoot, "project-")
        val release = Files.createDirectory(root.resolve("release files"))
        val project = ProjectBuilder.builder().withProjectDir(root.toFile()).build()
        val task = project.tasks.register("runDesktopC", LibfdxDesktopCRunTask::class.java).get()
        val windows = System.getProperty("os.name").lowercase().contains("windows")
        val java = File(System.getProperty("java.home"), "bin/java" + if(windows) ".exe" else "")
        val source = root.resolve("OutputChild.java")
        Files.writeString(source, """
            import java.nio.file.*;
            public class OutputChild {
                public static void main(String[] args) throws Exception {
                    byte[] block = new byte[1024];
                    java.util.Arrays.fill(block, (byte) 'x');
                    block[block.length - 1] = '\n';
                    for (int i = 0; i < 256; i++) {
                        System.out.write(block);
                        System.err.write(block);
                    }
                    System.out.flush();
                    System.err.flush();
                    Files.writeString(Path.of("result.txt"), args[0]);
                    System.exit(Integer.parseInt(args[1]));
                }
            }
        """.trimIndent())
        val executable = release.resolve("child_release" + if(windows) ".exe" else "")
        val arguments = if(windows) {
            // cmd.exe is standalone; its copied name exercises the task's actual executable selection.
            Files.copy(Path.of(System.getenv("SystemRoot"), "System32/cmd.exe"), executable)
            val script = root.resolve("child.cmd")
            Files.writeString(script, "@\"${java.absolutePath}\" \"$source\" %*\r\n@exit /b %errorlevel%\r\n")
            listOf("/d", "/c", script.toString(), "argument with spaces", exitCode.toString())
        } else {
            fun quote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"
            Files.writeString(executable, "#!/bin/sh\nexec ${quote(java.absolutePath)} ${quote(source.toString())} \"${'$'}@\"\n")
            check(executable.toFile().setExecutable(true))
            listOf("argument with spaces", exitCode.toString())
        }
        task.releaseDir.set(release.toFile())
        task.projectName.set("child")
        task.buildType.set("release")
        task.openConsole.set(false)
        task.runArgs.set(arguments)
        return task
    }
}
