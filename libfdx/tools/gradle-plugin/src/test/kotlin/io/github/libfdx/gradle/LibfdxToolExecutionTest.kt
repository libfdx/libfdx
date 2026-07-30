package io.github.libfdx.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class LibfdxToolExecutionTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `writes a versioned request with normalized indexed paths`() {
        val firstPath = temporaryDirectory.resolve("first").resolve("..").resolve("first")
        val secondPath = temporaryDirectory.resolve("second")
        val requestFile = temporaryDirectory.resolve("requests/tool.properties")

        LibfdxToolRequest().apply {
            value("enabled", true)
            paths("inputs", listOf(firstPath, secondPath))
        }.write(requestFile.toFile())

        val properties = Properties()
        Files.newBufferedReader(requestFile).use(properties::load)
        assertEquals("1", properties.getProperty("formatVersion"))
        assertEquals("true", properties.getProperty("enabled"))
        assertEquals("2", properties.getProperty("inputs.count"))
        assertEquals(
            firstPath.toAbsolutePath().normalize().toString(),
            properties.getProperty("inputs.0")
        )
        assertEquals(
            secondPath.toAbsolutePath().normalize().toString(),
            properties.getProperty("inputs.1")
        )
    }

    @Test
    fun `collects sorted web assets with later roots overriding earlier roots`() {
        val firstRoot = temporaryDirectory.resolve("first-assets")
        val secondRoot = temporaryDirectory.resolve("second-assets")
        val looseAsset = temporaryDirectory.resolve("loose.dat")
        Files.createDirectories(firstRoot.resolve("nested"))
        Files.createDirectories(secondRoot)
        Files.write(firstRoot.resolve("shared.txt"), byteArrayOf(1))
        Files.write(firstRoot.resolve("nested/texture.bin"), byteArrayOf(2, 3))
        Files.write(secondRoot.resolve("shared.txt"), byteArrayOf(4, 5, 6))
        Files.write(looseAsset, byteArrayOf(7, 8, 9, 10))

        val assets = collectLibfdxWebAssets(
            listOf(firstRoot.toFile(), secondRoot.toFile(), looseAsset.toFile())
        )

        assertEquals(
            listOf(
                LibfdxWebAsset("loose.dat", 4L),
                LibfdxWebAsset("nested/texture.bin", 2L),
                LibfdxWebAsset("shared.txt", 3L)
            ),
            assets
        )
    }

    @Test
    fun `keeps configured web logo metadata owned by the project`() {
        val assetRoot = temporaryDirectory.resolve("assets")
        Files.createDirectories(assetRoot)
        Files.write(assetRoot.resolve("fdx_logo_dark.png"), byteArrayOf(1, 2, 3, 4, 5))

        val assets = collectLibfdxWebAssets(listOf(assetRoot.toFile()))

        assertEquals(
            listOf(LibfdxWebAsset("fdx_logo_dark.png", 5L)),
            assets
        )
    }
}
