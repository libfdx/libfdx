package io.github.libfdx.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DesktopCProjectTaskTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `copies configured assets into a clean release asset directory`() {
        val projectDirectory = temporaryDirectory.resolve("project")
        val generatedSources = temporaryDirectory.resolve("generated")
        val assetRoot = temporaryDirectory.resolve("sample-assets")
        val releaseDirectory = temporaryDirectory.resolve("release")
        Files.createDirectories(projectDirectory)
        Files.createDirectories(generatedSources)
        Files.createDirectories(assetRoot.resolve("textures"))
        Files.createDirectories(releaseDirectory.resolve("assets"))
        Files.write(assetRoot.resolve("textures/player.png"), byteArrayOf(1, 2, 3))
        Files.writeString(releaseDirectory.resolve("assets/stale.txt"), "stale")

        val project = ProjectBuilder.builder()
            .withProjectDir(projectDirectory.toFile())
            .build()
        val task = project.tasks.register("desktopCProject", LibfdxDesktopCProjectTask::class.java).get()
        task.buildRoot.set(temporaryDirectory.resolve("dist").toFile())
        task.generatedSourcesDir.set(generatedSources.toFile())
        task.releaseDir.set(releaseDirectory.toFile())
        task.projectName.set("sample")
        task.buildType.set("Debug")
        task.showConsole.set(true)
        task.assets.from(assetRoot.toFile())

        task.writeProject()

        assertArrayEquals(
            byteArrayOf(1, 2, 3),
            Files.readAllBytes(releaseDirectory.resolve("assets/textures/player.png"))
        )
        assertFalse(Files.exists(releaseDirectory.resolve("assets/stale.txt")))
    }
}
