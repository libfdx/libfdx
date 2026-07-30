package io.github.libfdx.gradle

import org.gradle.api.file.FileCollection
import org.gradle.process.ExecOperations
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

// Mirrors BitmapFontSpec.DEFAULT_ASSET_PATH. Kept separate because the Gradle
// plugin must not compile against libFDX tool modules. Update both together.
internal const val DEFAULT_BITMAP_FONT_ASSET_PATH = "font/bitmap"
internal const val BITMAP_FONT_TOOL_CLASS = "io.github.libfdx.tools.font.BitmapFontTool"
internal const val SHADER_VALIDATION_TOOL_CLASS = "io.github.libfdx.tools.shader.ShaderValidationTool"
internal const val WEB_APP_TOOL_CLASS = "io.github.libfdx.backend.web.WebAppTool"
internal const val DESKTOP_C_PROJECT_TOOL_CLASS = "io.github.libfdx.backend.desktopc.NativeProjectTool"
internal const val PSP_PROJECT_TOOL_CLASS = "io.github.libfdx.backend.psp.PspProjectTool"
internal const val IOS_C_PROJECT_TOOL_CLASS = "io.github.libfdx.backend.iosc.IosCProjectTool"

internal class LibfdxToolRequest {
    private val properties = Properties().apply {
        setProperty("formatVersion", "1")
    }

    fun value(name: String, value: Any) {
        properties.setProperty(name, value.toString())
    }

    fun paths(name: String, values: Iterable<Path>) {
        val paths = values.toList()
        value("$name.count", paths.size)
        paths.forEachIndexed { index, path ->
            value("$name.$index", path.toAbsolutePath().normalize())
        }
    }

    fun write(file: File): File {
        file.parentFile.mkdirs()
        Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8).use { writer ->
            properties.store(writer, null)
        }
        return file
    }
}

internal fun executeLibfdxTool(
    execOperations: ExecOperations,
    toolClasspath: FileCollection,
    mainClass: String,
    requestFile: File
) {
    execOperations.javaexec {
        classpath(toolClasspath)
        this.mainClass.set(mainClass)
        args(requestFile.absolutePath)
    }.assertNormalExitValue()
}

internal data class LibfdxWebAsset(
    val path: String,
    val size: Long
)

internal fun collectLibfdxWebAssets(assetRoots: Iterable<File>): List<LibfdxWebAsset> {
    val assets = linkedMapOf<String, LibfdxWebAsset>()
    assetRoots.forEach { root ->
        val normalizedRoot = root.toPath().toAbsolutePath().normalize()
        when {
            Files.isDirectory(normalizedRoot) -> Files.walk(normalizedRoot).use { stream ->
                stream.filter(Files::isRegularFile).forEach { file ->
                    val relative = normalizedRoot.relativize(file).toString().replace('\\', '/')
                    assets[relative] = LibfdxWebAsset(relative, Files.size(file))
                }
            }
            Files.isRegularFile(normalizedRoot) -> {
                val name = normalizedRoot.fileName.toString()
                assets[name] = LibfdxWebAsset(name, Files.size(normalizedRoot))
            }
        }
    }
    return assets.values.sortedBy { asset -> asset.path }
}

internal const val WEB_ASSET_COUNT_PROPERTY = "libfdx.web.assets.count"
internal const val WEB_ASSET_ENTRY_PROPERTY_PREFIX = "libfdx.web.assets."
