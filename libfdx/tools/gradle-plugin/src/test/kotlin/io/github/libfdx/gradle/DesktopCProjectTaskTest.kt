package io.github.libfdx.gradle

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
        val assetRoot = temporaryDirectory.resolve("sample-assets")
        val releaseDirectory = temporaryDirectory.resolve("release")
        Files.createDirectories(assetRoot.resolve("textures"))
        Files.createDirectories(releaseDirectory.resolve("assets"))
        Files.write(assetRoot.resolve("textures/player.png"), byteArrayOf(1, 2, 3))
        Files.writeString(releaseDirectory.resolve("assets/stale.txt"), "stale")

        copyAssetRoots(listOf(assetRoot.toFile()), releaseDirectory.resolve("assets").toFile())

        assertArrayEquals(
            byteArrayOf(1, 2, 3),
            Files.readAllBytes(releaseDirectory.resolve("assets/textures/player.png"))
        )
        assertFalse(Files.exists(releaseDirectory.resolve("assets/stale.txt")))
    }
}
