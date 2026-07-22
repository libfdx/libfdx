package io.github.libfdx.gradle

import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.TreeMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

internal const val ECS_PROJECT_BUNDLE_TASK = "libfdx_ecs_project_bundle"
internal const val ECS_PROJECT_FORMAT = "libfdx.ecs.project"
internal const val ECS_PROJECT_FORMAT_VERSION = 1
internal const val ECS_BUNDLE_FORMAT = "libfdx.ecs.project-bundle"
internal const val ECS_BUNDLE_FORMAT_VERSION = 1

abstract class LibfdxEcsProjectBundleTask : DefaultTask() {
    @get:Input
    abstract val projectId: Property<String>

    @get:Input
    abstract val entryClass: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectManifest: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetsDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val scenesDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectClasses: ConfigurableFileCollection

    @get:Classpath
    abstract val allowedDependencies: ConfigurableFileCollection

    @get:Input
    abstract val toolingAbi: Property<Int>

    @get:Input
    abstract val libfdxAbi: Property<String>

    @get:Input
    abstract val gradleRoot: Property<String>

    @get:Input
    abstract val gradleProject: Property<String>

    @get:Input
    abstract val desktopBundleTask: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun buildBundle() {
        writeEcsProjectBundle(
            EcsProjectBundleSpec(
                projectId.get(),
                entryClass.get(),
                projectManifest.get().asFile,
                assetsDirectory.get().asFile,
                scenesDirectory.get().asFile,
                projectClasses.files,
                allowedDependencies.files,
                toolingAbi.get(),
                libfdxAbi.get(),
                gradleRoot.get(),
                gradleProject.get(),
                desktopBundleTask.get(),
                outputFile.get().asFile
            )
        )
    }
}

internal data class EcsProjectBundleSpec(
    val projectId: String,
    val entryClass: String,
    val projectManifest: File,
    val assetsDirectory: File,
    val scenesDirectory: File,
    val projectClasses: Set<File>,
    val allowedDependencies: Set<File>,
    val toolingAbi: Int,
    val libfdxAbi: String,
    val gradleRoot: String,
    val gradleProject: String,
    val desktopBundleTask: String,
    val outputFile: File
)

internal fun writeEcsProjectBundle(spec: EcsProjectBundleSpec) {
    validateSpec(spec)
    val manifestBytes = Files.readAllBytes(spec.projectManifest.toPath())
    val manifest = parseAndValidateManifest(spec, manifestBytes)
    val entries = TreeMap<String, ByteArray>()
    addEntry(entries, "fdx-project.json", manifestBytes)
    addProjectClasses(entries, spec.projectClasses)
    val libraries = addAllowedDependencies(entries, spec.allowedDependencies)
    val assetPrefix = normalizedPath(manifest.requiredString("assetsDirectory"), "assetsDirectory")
    addDirectory(entries, spec.assetsDirectory, assetPrefix)
    addDirectory(entries, spec.scenesDirectory, "scenes")

    val defaultScene = normalizedPath(manifest.requiredString("defaultScene"), "defaultScene")
    if (!entries.containsKey(defaultScene)) {
        throw GradleException("Default scene '$defaultScene' is not present in the project bundle.")
    }

    addEntry(entries, "META-INF/fdx-bundle.json", bundleMetadata(spec, libraries))
    val hashes = StringBuilder(entries.size * 96)
    entries.forEach { (path, bytes) ->
        hashes.append(sha256(bytes)).append("  ").append(path).append('\n')
    }
    addEntry(entries, "META-INF/fdx-hashes.sha256", hashes.toString().toByteArray(StandardCharsets.UTF_8))

    spec.outputFile.parentFile?.mkdirs()
    ZipOutputStream(spec.outputFile.outputStream().buffered()).use { zip ->
        entries.forEach { (path, bytes) ->
            val entry = ZipEntry(path)
            entry.time = 0L
            zip.putNextEntry(entry)
            zip.write(bytes)
            zip.closeEntry()
        }
    }
}

private fun validateSpec(spec: EcsProjectBundleSpec) {
    requireValue(spec.projectId, "projectId")
    requireValue(spec.entryClass, "entryClass")
    requireValue(spec.libfdxAbi, "libfdxAbi")
    requireValue(spec.gradleRoot, "gradleRoot")
    requireValue(spec.gradleProject, "gradleProject")
    if (spec.toolingAbi < 1) {
        throw GradleException("toolingAbi must be at least 1.")
    }
    if (!spec.projectManifest.isFile) {
        throw GradleException("Missing checked-in project manifest: ${spec.projectManifest}")
    }
    if (!spec.assetsDirectory.isDirectory) {
        throw GradleException("Missing project assets directory: ${spec.assetsDirectory}")
    }
    if (!spec.scenesDirectory.isDirectory) {
        throw GradleException("Missing project scenes directory: ${spec.scenesDirectory}")
    }
}

private fun parseAndValidateManifest(spec: EcsProjectBundleSpec, bytes: ByteArray): ManifestMap {
    val parsed = JsonSlurper().parse(bytes.inputStream(), StandardCharsets.UTF_8.name()) as? Map<*, *>
        ?: throw GradleException("fdx-project.json must contain one JSON object.")
    val manifest = ManifestMap(parsed)
    manifest.requireEquals("format", ECS_PROJECT_FORMAT)
    manifest.requireNumber("formatVersion", ECS_PROJECT_FORMAT_VERSION)
    manifest.requireEquals("id", spec.projectId)
    manifest.requireEquals("entryClass", spec.entryClass)
    manifest.requireEquals("gradleRoot", spec.gradleRoot.replace('\\', '/'))
    manifest.requireEquals("gradleProject", spec.gradleProject)
    manifest.requireEquals("desktopBundleTask", spec.desktopBundleTask)
    normalizedPath(manifest.requiredString("defaultScene"), "defaultScene")
    normalizedPath(manifest.requiredString("assetsDirectory"), "assetsDirectory")
    return manifest
}

private fun addProjectClasses(entries: MutableMap<String, ByteArray>, classpath: Set<File>) {
    classpath.sortedBy { it.absolutePath.replace('\\', '/') }.forEach { source ->
        when {
            source.isDirectory -> addClassDirectory(entries, source)
            source.isFile && source.extension.equals("jar", ignoreCase = true) -> addProjectJar(entries, source)
            source.exists() -> throw GradleException("Project class input must be a directory or jar: $source")
        }
    }
}

private fun addClassDirectory(entries: MutableMap<String, ByteArray>, directory: File) {
    Files.walk(directory.toPath()).use { paths ->
        paths.filter(Files::isRegularFile)
            .sorted()
            .forEach { path ->
                val relative = directory.toPath().relativize(path).toString().replace('\\', '/')
                validateProjectClassPath(relative)
                addEntry(entries, "classes/$relative", Files.readAllBytes(path))
            }
    }
}

private fun addProjectJar(entries: MutableMap<String, ByteArray>, jar: File) {
    ZipFile(jar).use { zip ->
        zip.entries().asSequence()
            .filter { !it.isDirectory }
            .map { it.name to zip.getInputStream(it).use { input -> input.readBytes() } }
            .sortedBy { it.first }
            .forEach { (path, bytes) ->
                val normalized = normalizedPath(path, "project jar entry")
                if (!normalized.equals("META-INF/MANIFEST.MF", ignoreCase = true)) {
                    validateProjectClassPath(normalized)
                    addEntry(entries, "classes/$normalized", bytes)
                }
            }
    }
}

private fun addAllowedDependencies(entries: MutableMap<String, ByteArray>, dependencies: Set<File>): List<String> {
    val libraryPaths = ArrayList<String>(dependencies.size)
    dependencies.sortedBy { it.name }.forEach { dependency ->
        if (!dependency.isFile || !dependency.extension.equals("jar", ignoreCase = true)) {
            throw GradleException("Allowed project dependency must be a jar: $dependency")
        }
        rejectProtectedDependencyClasses(dependency)
        val path = "lib/${normalizedPath(dependency.name, "dependency file name")}"
        addEntry(entries, path, Files.readAllBytes(dependency.toPath()))
        libraryPaths.add(path)
    }
    return libraryPaths
}

private fun rejectProtectedDependencyClasses(dependency: File) {
    ZipFile(dependency).use { zip ->
        val protected = zip.entries().asSequence()
            .map { it.name.replace('\\', '/') }
            .firstOrNull { path -> DEPENDENCY_PROTECTED_PREFIXES.any(path::startsWith) }
        if (protected != null) {
            throw GradleException("Allowed dependency '${dependency.name}' contains protected class '$protected'.")
        }
    }
}

private fun addDirectory(entries: MutableMap<String, ByteArray>, directory: File, prefix: String) {
    Files.walk(directory.toPath()).use { paths ->
        paths.filter(Files::isRegularFile)
            .sorted()
            .forEach { path ->
                val relative = directory.toPath().relativize(path).toString().replace('\\', '/')
                addEntry(entries, "$prefix/${normalizedPath(relative, "project file")}", Files.readAllBytes(path))
            }
    }
}

private fun addEntry(entries: MutableMap<String, ByteArray>, path: String, bytes: ByteArray) {
    val normalized = normalizedPath(path, "bundle entry")
    if (entries.putIfAbsent(normalized, bytes) != null) {
        throw GradleException("Duplicate project bundle entry '$normalized'.")
    }
}

private fun validateProjectClassPath(path: String) {
    val normalized = normalizedPath(path, "project class")
    val protected = PROJECT_PROTECTED_PATHS.any { candidate ->
        normalized == candidate || normalized.startsWith(candidate)
    }
    if (protected) {
        throw GradleException("Project bundle contains protected class '$normalized'.")
    }
}

private fun normalizedPath(value: String, field: String): String {
    val normalized = value.trim().replace('\\', '/')
    if (normalized.isEmpty() || normalized.startsWith('/') || normalized.contains(":/") ||
        normalized.split('/').any { it.isEmpty() || it == "." || it == ".." }) {
        throw GradleException("$field must be a non-empty portable relative path: '$value'.")
    }
    return normalized
}

private fun bundleMetadata(spec: EcsProjectBundleSpec, libraries: List<String>): ByteArray {
    val json = buildString(256 + libraries.size * 48) {
        append("{\n")
        append("  \"format\": \"").append(jsonEscape(ECS_BUNDLE_FORMAT)).append("\",\n")
        append("  \"formatVersion\": ").append(ECS_BUNDLE_FORMAT_VERSION).append(",\n")
        append("  \"projectId\": \"").append(jsonEscape(spec.projectId)).append("\",\n")
        append("  \"entryClass\": \"").append(jsonEscape(spec.entryClass)).append("\",\n")
        append("  \"toolingAbi\": ").append(spec.toolingAbi).append(",\n")
        append("  \"libfdxAbi\": \"").append(jsonEscape(spec.libfdxAbi)).append("\",\n")
        append("  \"classesDirectory\": \"classes\",\n")
        append("  \"libraries\": [")
        libraries.forEachIndexed { index, path ->
            if (index > 0) append(", ")
            append('"').append(jsonEscape(path)).append('"')
        }
        append("]\n}\n")
    }
    return json.toByteArray(StandardCharsets.UTF_8)
}

private fun sha256(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    val result = StringBuilder(digest.size * 2)
    digest.forEach { value -> result.append("%02x".format(value.toInt() and 0xff)) }
    return result.toString()
}

private fun jsonEscape(value: String): String {
    val output = StringBuilder(value.length + 8)
    value.forEach { character ->
        when (character) {
            '\\' -> output.append("\\\\")
            '"' -> output.append("\\\"")
            '\n' -> output.append("\\n")
            '\r' -> output.append("\\r")
            '\t' -> output.append("\\t")
            else -> if (character.code < 0x20) {
                output.append("\\u").append(character.code.toString(16).padStart(4, '0'))
            } else {
                output.append(character)
            }
        }
    }
    return output.toString()
}

private fun requireValue(value: String, name: String) {
    if (value.isBlank()) {
        throw GradleException("$name must not be blank.")
    }
}

private class ManifestMap(private val values: Map<*, *>) {
    fun requiredString(name: String): String {
        return values[name] as? String
            ?: throw GradleException("fdx-project.json field '$name' must be a string.")
    }

    fun requireEquals(name: String, expected: String) {
        val actual = requiredString(name)
        if (actual != expected) {
            throw GradleException("fdx-project.json field '$name' is '$actual'; expected '$expected'.")
        }
    }

    fun requireNumber(name: String, expected: Int) {
        val actual = values[name] as? Number
            ?: throw GradleException("fdx-project.json field '$name' must be a number.")
        if (actual.toInt() != expected) {
            throw GradleException("fdx-project.json field '$name' is '$actual'; expected '$expected'.")
        }
    }
}

private val PROJECT_PROTECTED_PATHS = listOf(
    "io/github/libfdx/Fdx.class",
    "io/github/libfdx/application/",
    "io/github/libfdx/assets/",
    "io/github/libfdx/backend/",
    "io/github/libfdx/camera/",
    "io/github/libfdx/collections/",
    "io/github/libfdx/core/",
    "io/github/libfdx/display/",
    "io/github/libfdx/ecs/",
    "io/github/libfdx/editor/",
    "io/github/libfdx/engine/",
    "io/github/libfdx/files/",
    "io/github/libfdx/graphics/",
    "io/github/libfdx/input/",
    "io/github/libfdx/json/",
    "io/github/libfdx/math/",
    "io/github/libfdx/net/",
    "io/github/libfdx/storage/",
    "io/github/libfdx/ui/",
    "com/github/xpenatan/jimgui/",
    "com/github/xpenatan/imgui/",
    "imgui/"
)

private val DEPENDENCY_PROTECTED_PREFIXES = listOf(
    "io/github/libfdx/",
    "com/github/xpenatan/jimgui/",
    "com/github/xpenatan/imgui/",
    "imgui/"
)
