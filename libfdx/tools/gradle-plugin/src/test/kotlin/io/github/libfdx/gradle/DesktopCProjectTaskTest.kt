package io.github.libfdx.gradle

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

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

    @Test
    fun `copies shared dependency assets without replacing application overrides`() {
        val releaseAssets = temporaryDirectory.resolve("release/assets")
        val applicationFont = releaseAssets.resolve("libfdx-assets/ui/font/default.ttf")
        Files.createDirectories(applicationFont.parent)
        Files.write(applicationFont, byteArrayOf(9))
        val dependencyJar = temporaryDirectory.resolve("ui-kit.jar")
        JarOutputStream(Files.newOutputStream(dependencyJar)).use { output ->
            output.putNextEntry(JarEntry("libfdx-assets/ui/font/default.ttf"))
            output.write(byteArrayOf(1, 2, 3))
            output.closeEntry()
            output.putNextEntry(JarEntry("libfdx-assets/ui/font/OFL.txt"))
            output.write("license".toByteArray())
            output.closeEntry()
        }

        copySharedAssetResources(listOf(dependencyJar.toFile()), releaseAssets.toFile())

        assertArrayEquals(byteArrayOf(9), Files.readAllBytes(applicationFont))
        assertEquals("license", Files.readString(releaseAssets.resolve("libfdx-assets/ui/font/OFL.txt")))
    }
}
