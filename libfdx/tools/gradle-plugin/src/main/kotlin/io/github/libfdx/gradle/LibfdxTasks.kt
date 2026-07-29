package io.github.libfdx.gradle

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.libfdx.backend.desktopc.NativeProject
import io.github.libfdx.backend.desktopc.NativeProjectWriter
import io.github.libfdx.backend.iosc.IosCGraphicsApi
import io.github.libfdx.backend.iosc.IosCProject
import io.github.libfdx.backend.iosc.IosCProjectWriter
import io.github.libfdx.backend.psp.PspProject
import io.github.libfdx.backend.psp.PspProjectWriter
import io.github.libfdx.backend.web.WebApp
import io.github.libfdx.backend.web.WebAppWriter
import io.github.libfdx.graphics.shader.ShaderProfile
import io.github.libfdx.graphics.shader.ShaderProfileValidator
import io.github.libfdx.graphics.shader.ShaderValidationDiagnostic
import io.github.libfdx.graphics.shader.ShaderValidationSeverity
import io.github.libfdx.tools.font.BitmapFontGenerator
import io.github.libfdx.tools.font.BitmapFontSpec
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.gradle.process.ExecOperations
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.InetSocketAddress
import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CountDownLatch
import java.util.zip.ZipInputStream
import javax.inject.Inject

internal fun androidStartCommand(
    adbExecutable: String,
    applicationId: String,
    activity: String,
    stringExtras: Map<String, String>,
    booleanExtras: Map<String, String>
): List<String> {
    val command = mutableListOf(
        adbExecutable,
        "shell",
        "am",
        "start",
        "-n",
        "$applicationId/$activity"
    )
    stringExtras.forEach { (name, value) ->
        command.add("--es")
        command.add(name)
        command.add(value)
    }
    booleanExtras.forEach { (name, value) ->
        command.add("--ez")
        command.add(name)
        command.add(value)
    }
    return command
}

@DisableCachingByDefault(because = "Installs and launches an Android application on a connected device")
abstract class LibfdxAndroidRunTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @get:Internal
    abstract val adbExecutable: RegularFileProperty

    @get:Input
    abstract val applicationId: Property<String>

    @get:Input
    abstract val activity: Property<String>

    @get:Input
    abstract val stringExtras: MapProperty<String, String>

    @get:Input
    abstract val booleanExtras: MapProperty<String, String>

    @TaskAction
    fun launch() {
        val command = androidStartCommand(
            adbExecutable.get().asFile.absolutePath,
            applicationId.get(),
            activity.get(),
            stringExtras.get(),
            booleanExtras.get()
        )
        execOperations.exec {
            commandLine(command)
        }.assertNormalExitValue()
    }
}

abstract class LibfdxBitmapFontTask : DefaultTask() {
    @get:Input
    abstract val fontName: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFile: RegularFileProperty

    @get:Input
    abstract val assetPath: Property<String>

    @get:Input
    abstract val size: Property<Int>

    @get:Input
    abstract val padding: Property<Int>

    @get:Input
    abstract val maxTextureSize: Property<Int>

    @get:Input
    abstract val characters: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val result = BitmapFontGenerator.generate(
            BitmapFontSpec.builder()
                .sourceFile(sourceFile.get().asFile.toPath())
                .outputDirectory(outputDir.get().asFile.toPath())
                .name(fontName.get())
                .assetPath(assetPath.get())
                .size(size.get())
                .padding(padding.get())
                .maxTextureSize(maxTextureSize.get())
                .characters(characters.get())
                .build()
        )
        logger.lifecycle("Generated libfdx bitmap font ${result.assetFontPath()}")
    }
}

abstract class LibfdxWebAppTask : DefaultTask() {
    @get:OutputDirectory
    abstract val webappDir: DirectoryProperty

    @get:Input
    abstract val title: Property<String>

    @get:Input
    abstract val width: Property<Int>

    @get:Input
    abstract val height: Property<Int>

    @get:Input
    abstract val canvasId: Property<String>

    @get:Input
    abstract val entryPointName: Property<String>

    @get:Input
    abstract val mainClassArgs: Property<String>

    @get:Input
    abstract val targetFileName: Property<String>

    @get:Input
    abstract val wasm: Property<Boolean>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assets: ConfigurableFileCollection

    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    @TaskAction
    fun writeWebApp() {
        val webappDirectory = webappDir.get().asFile.toPath()
        val assetPaths = assets.files.map { it.toPath() }
        val runtimeClasspathPaths = runtimeClasspath.files.map { it.toPath() }
        if(writeWebAppWithRuntimeClasspathWriter(webappDirectory, assetPaths, runtimeClasspathPaths)) {
            return
        }
        WebAppWriter.write(
            WebApp.builder()
                .webappDirectory(webappDirectory)
                .title(title.get())
                .width(width.get())
                .height(height.get())
                .canvasId(canvasId.get())
                .entryPointName(entryPointName.get())
                .mainClassArgs(mainClassArgs.get())
                .targetFileName(targetFileName.get())
                .wasm(wasm.get())
                .assets(assetPaths)
                .runtimeClasspath(runtimeClasspathPaths)
                .build()
        )
    }

    private fun writeWebAppWithRuntimeClasspathWriter(
        webappDirectory: Path,
        assetPaths: List<Path>,
        runtimeClasspathPaths: List<Path>
    ): Boolean {
        val urls = runtimeClasspathPaths
            .filter { Files.exists(it) }
            .map { it.toUri().toURL() }
            .toTypedArray()
        if(urls.isEmpty()) {
            return false
        }
        val loader = BackendWebClassLoader(urls, WebAppWriter::class.java.classLoader)
        try {
            val appClass = try {
                loader.loadClass(WEB_APP_CLASS_NAME)
            }
            catch(_: ClassNotFoundException) {
                return false
            }
            val writerClass = loader.loadClass(WEB_APP_WRITER_CLASS_NAME)
            val builder = appClass.getMethod("builder").invoke(null)
            invokeBuilder(builder, "webappDirectory", Path::class.java, webappDirectory)
            invokeBuilder(builder, "title", String::class.java, title.get())
            invokeBuilder(builder, "width", Integer.TYPE, width.get())
            invokeBuilder(builder, "height", Integer.TYPE, height.get())
            invokeBuilder(builder, "canvasId", String::class.java, canvasId.get())
            invokeBuilder(builder, "entryPointName", String::class.java, entryPointName.get())
            invokeBuilder(builder, "mainClassArgs", String::class.java, mainClassArgs.get())
            invokeBuilder(builder, "targetFileName", String::class.java, targetFileName.get())
            invokeBuilder(builder, "wasm", java.lang.Boolean.TYPE, wasm.get())
            invokeBuilder(builder, "assets", Collection::class.java, assetPaths)
            invokeBuilder(builder, "runtimeClasspath", Collection::class.java, runtimeClasspathPaths)
            val app = builder.javaClass.getMethod("build").invoke(builder)
            writerClass.getMethod("write", appClass).invoke(null, app)
            return true
        }
        catch(error: InvocationTargetException) {
            val cause = error.targetException
            when(cause) {
                is RuntimeException -> throw cause
                is Error -> throw cause
                else -> throw GradleException("libFDX web app generation failed", cause)
            }
        }
        finally {
            loader.close()
        }
    }

    private fun invokeBuilder(builder: Any, name: String, parameterType: Class<*>, value: Any) {
        builder.javaClass.getMethod(name, parameterType).invoke(builder, value)
    }

    private companion object {
        const val WEB_APP_CLASS_NAME = "io.github.libfdx.backend.web.WebApp"
        const val WEB_APP_WRITER_CLASS_NAME = "io.github.libfdx.backend.web.WebAppWriter"
    }
}

private class BackendWebClassLoader(urls: Array<URL>, parent: ClassLoader) : URLClassLoader(urls, parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            if(name.startsWith(WEB_BACKEND_PACKAGE)) {
                val loaded = findLoadedClass(name)
                if(loaded != null) {
                    if(resolve) {
                        resolveClass(loaded)
                    }
                    return loaded
                }
                try {
                    val found = findClass(name)
                    if(resolve) {
                        resolveClass(found)
                    }
                    return found
                }
                catch(_: ClassNotFoundException) {
                }
            }
            return super.loadClass(name, resolve)
        }
    }

    private companion object {
        const val WEB_BACKEND_PACKAGE = "io.github.libfdx.backend.web."
    }
}

abstract class LibfdxValidateShadersTask : DefaultTask() {
    @get:Internal
    abstract val sourceDir: DirectoryProperty

    @get:Input
    val sourceDirPath: String
        get() = sourceDir.get().asFile.absolutePath

    @get:Input
    abstract val defaultProfile: Property<String>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun validate() {
        val root = sourceDir.get().asFile.toPath()
        val profile = profileFromId(defaultProfile.get(), ShaderProfile.PORTABLE_WEBGPU)
        val entries = validateDirectory(root, profile)
        val output = reportFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(toMarkdown(root, entries), Charsets.UTF_8)
        if(errorCount(entries) != 0) {
            throw GradleException("libFDX shader validation failed. See ${output.absolutePath}")
        }
        logger.lifecycle("Validated ${entries.size} libfdx shader source file(s): ${output.absolutePath}")
    }

    private fun validateDirectory(sourceDirectory: Path, defaultProfile: ShaderProfile): List<ShaderValidationEntry> {
        if(!Files.isDirectory(sourceDirectory)) {
            return emptyList()
        }
        return Files.walk(sourceDirectory).use { stream ->
            stream.filter(Files::isRegularFile)
                .filter { it.fileName.toString().endsWith(".wgsl") }
                .sorted()
                .map { validateFile(it, defaultProfile) }
                .toList()
        }
    }

    private fun validateFile(path: Path, defaultProfile: ShaderProfile): ShaderValidationEntry {
        val source = Files.readString(path)
        val profile = profileFromSource(source, defaultProfile)
        val result = ShaderProfileValidator.validateWgsl(profile, source)
        return ShaderValidationEntry(path, profile.id(), result.diagnostics())
    }

    private fun profileFromSource(source: String?, defaultProfile: ShaderProfile): ShaderProfile {
        if(source.isNullOrEmpty()) {
            return defaultProfile
        }
        source.lineSequence().take(32).forEach { line ->
            var trimmed = line.trim()
            if(trimmed.startsWith("//")) {
                trimmed = trimmed.substring(2).trim()
            }
            if(trimmed.startsWith(PROFILE_PREFIX)) {
                var value = trimmed.substring(PROFILE_PREFIX.length).trim()
                if(value.startsWith("=")) {
                    value = value.substring(1).trim()
                }
                return profileFromId(value, defaultProfile)
            }
        }
        return defaultProfile
    }

    private fun profileFromId(id: String?, fallback: ShaderProfile): ShaderProfile {
        return ShaderProfile.fromId(id, fallback)
    }

    private fun toMarkdown(root: Path, entries: List<ShaderValidationEntry>): String {
        return buildString {
            val errors = errorCount(entries)
            append("# libFDX Shader Validation\n\n")
            append("status: ").append(if(errors == 0) "PASS" else "FAIL").append('\n')
            append("shaders: ").append(entries.size).append('\n')
            append("errors: ").append(errors).append("\n\n")
            entries.forEach { entry ->
                append("## ").append(relative(root, entry.path)).append('\n')
                append("profile: ").append(entry.profileId).append('\n')
                if(entry.diagnostics.isEmpty()) {
                    append("result: PASS\n\n")
                }
                else {
                    append("result: FAIL\n")
                    entry.diagnostics.forEach { diagnostic ->
                        append("- ")
                            .append(diagnostic.severity())
                            .append(' ')
                            .append(diagnostic.code())
                            .append(": ")
                            .append(diagnostic.message())
                            .append('\n')
                    }
                    append('\n')
                }
            }
        }
    }

    private fun relative(root: Path, path: Path): String {
        return try {
            root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString()
                .replace('\\', '/')
        }
        catch(_: IllegalArgumentException) {
            path.toString().replace('\\', '/')
        }
    }

    private fun errorCount(entries: List<ShaderValidationEntry>): Int {
        return entries.sumOf { entry ->
            entry.diagnostics.count { it.severity() == ShaderValidationSeverity.ERROR }
        }
    }

    private data class ShaderValidationEntry(
        val path: Path,
        val profileId: String,
        val diagnostics: Array<ShaderValidationDiagnostic>
    )

    private companion object {
        const val PROFILE_PREFIX = "@fdx.profile"
    }
}

@DisableCachingByDefault(because = "Starts a blocking local HTTP server")
abstract class LibfdxRunWebTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val webappDir: DirectoryProperty

    @get:Input
    abstract val port: Property<Int>

    @get:Input
    abstract val defaultPath: Property<String>

    @TaskAction
    fun run() {
        val root = webappDir.get().asFile.canonicalFile
        val server = HttpServer.create(InetSocketAddress(port.get()), 0)
        server.createContext("/") { exchange ->
            serve(root, exchange)
        }
        server.executor = null
        val shutdownHook = Thread {
            server.stop(0)
        }
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        try {
            server.start()
            logger.lifecycle("Serving ${root.absolutePath} at http://localhost:${port.get()}${normalizedDefaultPath()}")
            CountDownLatch(1).await()
        }
        finally {
            server.stop(0)
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook)
            }
            catch(_: IllegalStateException) {
            }
        }
    }

    private fun normalizedDefaultPath(): String {
        val path = defaultPath.get().ifBlank { "/" }
        return if(path.startsWith("/")) path else "/$path"
    }

    private fun serve(root: File, exchange: HttpExchange) {
        val rawPath = exchange.requestURI.path.trimStart('/')
        val requested = File(root, if(rawPath.isEmpty()) "index.html" else rawPath).canonicalFile
        val file = if(requested.isDirectory) File(requested, "index.html") else requested
        if(!file.toPath().startsWith(root.toPath()) || !file.isFile) {
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
            return
        }
        val bytes = Files.readAllBytes(file.toPath())
        exchange.responseHeaders.add("Content-Type", contentType(file.name))
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun contentType(name: String): String {
        return when {
            name.endsWith(".html") -> "text/html; charset=utf-8"
            name.endsWith(".js") -> "text/javascript; charset=utf-8"
            name.endsWith(".wasm") -> "application/wasm"
            name.endsWith(".json") || name.endsWith(".gltf") -> "application/json; charset=utf-8"
            name.endsWith(".glb") -> "model/gltf-binary"
            name.endsWith(".bin") -> "application/octet-stream"
            name.endsWith(".txt") -> "text/plain; charset=utf-8"
            name.endsWith(".css") -> "text/css; charset=utf-8"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }
}

abstract class LibfdxDesktopCProjectTask : DefaultTask() {
    @get:OutputDirectory
    abstract val buildRoot: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedSourcesDir: DirectoryProperty

    @get:OutputDirectory
    abstract val releaseDir: DirectoryProperty

    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val buildType: Property<String>

    @get:Input
    abstract val showConsole: Property<Boolean>

    @get:Classpath
    abstract val nativeResourceClasspath: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assets: ConfigurableFileCollection

    @TaskAction
    fun writeProject() {
        NativeProjectWriter.write(
            NativeProject.builder()
                .buildRoot(buildRoot.get().asFile.toPath())
                .generatedSourcesDirectory(generatedSourcesDir.get().asFile.toPath())
                .releaseDirectory(releaseDir.get().asFile.toPath())
                .projectName(projectName.get())
                .buildType(buildType.get())
                .showConsole(showConsole.get())
                .nativeResourceClasspath(nativeResourceClasspath.files.map { it.toPath() })
                .build()
        )
        copyAssetRoots(assets.files, File(releaseDir.get().asFile, "assets"))
    }
}

abstract class LibfdxPspProjectTask : DefaultTask() {
    @get:OutputDirectory
    abstract val buildRoot: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedSourcesDir: DirectoryProperty

    @get:OutputDirectory
    abstract val releaseDir: DirectoryProperty

    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val debugMemory: Property<Boolean>

    @get:Classpath
    abstract val nativeResourceClasspath: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assets: ConfigurableFileCollection

    @TaskAction
    fun writeProject() {
        PspProjectWriter.write(
            PspProject.builder()
                .buildRoot(buildRoot.get().asFile.toPath())
                .generatedSourcesDirectory(generatedSourcesDir.get().asFile.toPath())
                .releaseDirectory(releaseDir.get().asFile.toPath())
                .projectName(projectName.get())
                .debugMemory(debugMemory.get())
                .nativeResourceClasspath(nativeResourceClasspath.files.map { it.toPath() })
                .assets(assets.files.map { it.toPath() })
                .build()
        )
    }
}

abstract class LibfdxIosCProjectTask : DefaultTask() {
    @get:OutputDirectory
    abstract val buildRoot: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedSourcesDir: DirectoryProperty

    @get:OutputDirectory
    abstract val releaseDir: DirectoryProperty

    @get:OutputDirectory
    abstract val xcodeProjectDir: DirectoryProperty

    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val bundleIdentifier: Property<String>

    @get:Input
    abstract val graphicsApi: Property<String>

    @get:Classpath
    abstract val nativeResourceClasspath: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assets: ConfigurableFileCollection

    @TaskAction
    fun writeProject() {
        IosCProjectWriter.write(
            IosCProject.builder()
                .buildRoot(buildRoot.get().asFile.toPath())
                .generatedSourcesDirectory(generatedSourcesDir.get().asFile.toPath())
                .releaseDirectory(releaseDir.get().asFile.toPath())
                .xcodeProjectDirectory(xcodeProjectDir.get().asFile.toPath())
                .projectName(projectName.get())
                .bundleIdentifier(bundleIdentifier.get())
                .graphicsApi(IosCGraphicsApi.fromId(graphicsApi.get()))
                .nativeResourceClasspath(nativeResourceClasspath.files.map { it.toPath() })
                .assets(assets.files.map { it.toPath() })
                .build()
        )
    }
}

@DisableCachingByDefault(because = "Runs external native build scripts")
abstract class LibfdxNativeBuildTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildRoot: DirectoryProperty

    @get:Input
    abstract val scriptBaseName: Property<String>

    @TaskAction
    fun build() {
        val root = buildRoot.get().asFile
        val script = File(root, scriptBaseName.get() + if(isWindows()) ".bat" else ".sh")
        if(!script.isFile) {
            throw IllegalStateException("Native build script was not generated: ${script.absolutePath}")
        }
        val command = if(isWindows()) listOf("cmd", "/c", script.absolutePath) else listOf("bash", script.absolutePath)
        val process = ProcessBuilder(command)
            .directory(root)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { logger.lifecycle(it) }
        }
        val exitCode = process.waitFor()
        if(exitCode != 0) {
            throw IllegalStateException("Native build failed with exit code $exitCode")
        }
    }
}

@DisableCachingByDefault(because = "Launches PPSSPP and captures a live emulator window")
abstract class LibfdxPspPpssppCaptureTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val releaseDir: DirectoryProperty

    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val ppssppExecutable: Property<String>

    @get:Input
    abstract val captureDelaySeconds: Property<Int>

    @get:Input
    abstract val ppssppAutoDownload: Property<Boolean>

    @get:Input
    abstract val ppssppDownloadUrl: Property<String>

    @get:OutputDirectory
    abstract val ppssppToolDir: DirectoryProperty

    @get:Input
    abstract val emulatorArgs: ListProperty<String>

    @get:OutputFile
    abstract val captureFile: RegularFileProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun capture() {
        if(!isWindows()) {
            throw IllegalStateException("PPSSPP window capture is currently implemented for Windows only.")
        }
        val eboot = File(releaseDir.get().asFile, "EBOOT.PBP")
        if(!eboot.isFile) {
            throw IllegalStateException("PSP EBOOT was not built: ${eboot.absolutePath}")
        }
        val executable = resolvePpssppExecutable(ppssppExecutable.orNull)
        val launchDirectory = preparePpssppGameDirectory(executable, projectName.get())
        val launchEboot = File(launchDirectory, "EBOOT.PBP")
        Files.copy(eboot.toPath(), launchEboot.toPath(), StandardCopyOption.REPLACE_EXISTING)
        val launchAssets = File(launchDirectory, "assets")
        val releaseAssets = File(releaseDir.get().asFile, "assets")
        if(releaseAssets.isDirectory) {
            copyDirectoryContents(releaseAssets, launchAssets)
        }
        val capture = captureFile.get().asFile
        capture.parentFile.mkdirs()
        if(capture.exists() && !capture.delete()) {
            throw IllegalStateException("Could not delete stale PPSSPP capture before running: ${capture.absolutePath}")
        }
        val configFile = writeCaptureConfig()
        val argsFile = File(temporaryDir, "ppsspp-args.txt")
        val screenshotDirsFile = File(temporaryDir, "ppsspp-screenshot-dirs.txt")
        argsFile.parentFile.mkdirs()
        val launchArgs = emulatorArgs.get() + listOf("--appendconfig=${configFile.absolutePath}")
        argsFile.writeText(launchArgs.joinToString(System.lineSeparator()), Charsets.UTF_8)
        screenshotDirsFile.writeText(
            ppssppScreenshotDirectories(executable).joinToString(System.lineSeparator()) { it.absolutePath },
            Charsets.UTF_8
        )
        val script = writeCaptureScript()
        val command = listOf(
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            script.absolutePath,
            "-Ppsspp",
            executable.absolutePath,
            "-Eboot",
            launchEboot.absolutePath,
            "-Capture",
            capture.absolutePath,
            "-DelaySeconds",
            captureDelaySeconds.get().toString(),
            "-ArgsFile",
            argsFile.absolutePath,
            "-ScreenshotDirsFile",
            screenshotDirsFile.absolutePath
        )
        val process = ProcessBuilder(command)
            .directory(project.projectDir)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { logger.lifecycle(it) }
        }
        val exitCode = process.waitFor()
        if(exitCode != 0) {
            throw IllegalStateException("PPSSPP capture failed with exit code $exitCode")
        }
        if(!capture.isFile || capture.length() == 0L) {
            throw IllegalStateException("PPSSPP capture was not written: ${capture.absolutePath}")
        }
        logger.lifecycle("Captured ${projectName.get()} PPSSPP frame: ${capture.absolutePath}")
    }

    private fun preparePpssppGameDirectory(executable: File, name: String): File {
        for(root in ppssppMemstickRoots(executable)) {
            val gameDirectory = File(root, "PSP/GAME/$name")
            try {
                deleteDirectory(gameDirectory)
                gameDirectory.mkdirs()
                if(gameDirectory.isDirectory) {
                    return gameDirectory
                }
            }
            catch(_: Exception) {
            }
        }
        val fallback = File(temporaryDir, "PSP/GAME/$name")
        deleteDirectory(fallback)
        fallback.mkdirs()
        return fallback
    }

    private fun resolvePpssppExecutable(configured: String?): File {
        val candidates = linkedSetOf<String>()
        if(!configured.isNullOrBlank()) {
            candidates.add(configured.trim())
        }
        System.getenv("PPSSPP_EXECUTABLE")?.takeIf { it.isNotBlank() }?.let(candidates::add)
        System.getenv("PPSSPP_HOME")?.takeIf { it.isNotBlank() }?.let { home ->
            candidates.add(File(home, "PPSSPPWindows64.exe").absolutePath)
            candidates.add(File(home, "PPSSPP.exe").absolutePath)
        }
        candidates.addAll(findPpssppOnPath())
        listOfNotNull(
            System.getenv("ProgramFiles"),
            System.getenv("ProgramFiles(x86)"),
            System.getenv("LOCALAPPDATA")?.let { File(it, "Programs").absolutePath }
        ).forEach { base ->
            candidates.add(File(base, "PPSSPP/PPSSPPWindows64.exe").absolutePath)
            candidates.add(File(base, "PPSSPP/PPSSPP.exe").absolutePath)
        }
        for(candidate in candidates) {
            val file = File(candidate)
            if(file.isFile) {
                return file
            }
        }
        findPpssppUnder(ppssppToolDir.get().asFile)?.let { return it }
        if(ppssppAutoDownload.get()) {
            return downloadAndExtractPpsspp()
        }
        throw ppssppNotFound()
    }

    private fun downloadAndExtractPpsspp(): File {
        val toolDir = ppssppToolDir.get().asFile
        val zip = File(toolDir, "ppsspp.zip")
        val extractDir = File(toolDir, "portable")
        toolDir.mkdirs()
        val url = ppssppDownloadUrl.get()
        logger.lifecycle("PPSSPP executable was not found; downloading portable PPSSPP from $url")
        URI(url).toURL().openStream().use { input ->
            Files.copy(input, zip.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        deleteDirectory(extractDir)
        unzip(zip, extractDir)
        val executable = findPpssppUnder(extractDir) ?: throw IllegalStateException(
            "Downloaded PPSSPP archive did not contain PPSSPPWindows64.exe or PPSSPP.exe: ${zip.absolutePath}"
        )
        logger.lifecycle("Using downloaded PPSSPP executable: ${executable.absolutePath}")
        return executable
    }

    private fun unzip(zip: File, outputDir: File) {
        val outputRoot = outputDir.canonicalFile.toPath()
        outputDir.mkdirs()
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            var entry = input.nextEntry
            while(entry != null) {
                val output = File(outputDir, entry.name).canonicalFile
                if(!output.toPath().startsWith(outputRoot)) {
                    throw IllegalStateException("Refusing to extract PPSSPP archive entry outside tool directory: ${entry.name}")
                }
                if(entry.isDirectory) {
                    output.mkdirs()
                }
                else {
                    output.parentFile.mkdirs()
                    output.outputStream().buffered().use { input.copyTo(it) }
                }
                input.closeEntry()
                entry = input.nextEntry
            }
        }
    }

    private fun findPpssppUnder(root: File): File? {
        if(!root.isDirectory) {
            return null
        }
        return root.walkTopDown()
            .filter { it.isFile && it.name in PPSSPP_EXECUTABLE_NAMES }
            .firstOrNull()
    }

    private fun ppssppNotFound(): IllegalStateException {
        return IllegalStateException(
            "PPSSPP executable was not found. Set PPSSPP_EXECUTABLE, PPSSPP_HOME, " +
                    "-Plibfdx.psp.ppssppExecutable=C:\\path\\to\\PPSSPPWindows64.exe, " +
                    "or enable -Plibfdx.psp.ppssppAutoDownload=true"
        )
    }

    private fun findPpssppOnPath(): List<String> {
        return PPSSPP_EXECUTABLE_NAMES.mapNotNull { name ->
            try {
                val process = ProcessBuilder("where.exe", name)
                    .redirectErrorStream(true)
                    .start()
                val matches = process.inputStream.bufferedReader().readLines()
                if(process.waitFor() == 0) {
                    matches.firstOrNull { it.isNotBlank() }
                }
                else {
                    null
                }
            }
            catch(_: Exception) {
                null
            }
        }
    }

    private fun writeCaptureScript(): File {
        val script = File(temporaryDir, "capture-ppsspp.ps1")
        script.writeText("""
            param(
                [Parameter(Mandatory=${'$'}true)][string]${'$'}Ppsspp,
                [Parameter(Mandatory=${'$'}true)][string]${'$'}Eboot,
                [Parameter(Mandatory=${'$'}true)][string]${'$'}Capture,
                [Parameter(Mandatory=${'$'}true)][int]${'$'}DelaySeconds,
                [Parameter(Mandatory=${'$'}true)][string]${'$'}ArgsFile,
                [Parameter(Mandatory=${'$'}true)][string]${'$'}ScreenshotDirsFile
            )

            ${'$'}ErrorActionPreference = 'Stop'
            Add-Type -AssemblyName System.Drawing
            Add-Type @"
            using System;
            using System.Runtime.InteropServices;
            using System.Text;

            public static class LibfdxWindowCapture {
                private const uint MF_BYPOSITION = 0x400;
                private const uint WM_COMMAND = 0x0111;
                private const uint INVALID_MENU_ID = 0xFFFFFFFF;

                [StructLayout(LayoutKind.Sequential)]
                public struct RECT {
                    public int Left;
                    public int Top;
                    public int Right;
                    public int Bottom;
                }

                [StructLayout(LayoutKind.Sequential)]
                public struct POINT {
                    public int X;
                    public int Y;
                }

                [DllImport("user32.dll")]
                public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);

                [DllImport("user32.dll")]
                public static extern bool GetClientRect(IntPtr hWnd, out RECT lpRect);

                [DllImport("user32.dll")]
                public static extern bool ClientToScreen(IntPtr hWnd, ref POINT lpPoint);

                [DllImport("user32.dll")]
                public static extern bool SetForegroundWindow(IntPtr hWnd);

                [DllImport("user32.dll")]
                public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

                [DllImport("user32.dll")]
                public static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);

                [DllImport("user32.dll")]
                public static extern IntPtr GetMenu(IntPtr hWnd);

                [DllImport("user32.dll")]
                public static extern int GetMenuItemCount(IntPtr hMenu);

                [DllImport("user32.dll")]
                public static extern IntPtr GetSubMenu(IntPtr hMenu, int nPos);

                [DllImport("user32.dll")]
                public static extern uint GetMenuItemID(IntPtr hMenu, int nPos);

                [DllImport("user32.dll", CharSet = CharSet.Unicode)]
                public static extern int GetMenuString(IntPtr hMenu, int uIDItem, StringBuilder lpString, int nMaxCount, uint uFlag);

                [DllImport("user32.dll")]
                public static extern IntPtr SendMessage(IntPtr hWnd, uint Msg, IntPtr wParam, IntPtr lParam);

                public static bool SendMenuCommandByText(IntPtr hWnd, string text) {
                    IntPtr menu = GetMenu(hWnd);
                    if (menu == IntPtr.Zero) {
                        return false;
                    }
                    return SendMenuCommandByText(hWnd, menu, text, 0);
                }

                private static bool SendMenuCommandByText(IntPtr hWnd, IntPtr menu, string text, int depth) {
                    if (depth > 8) {
                        return false;
                    }
                    int count = GetMenuItemCount(menu);
                    for (int i = 0; i < count; i++) {
                        StringBuilder label = new StringBuilder(256);
                        GetMenuString(menu, i, label, label.Capacity, MF_BYPOSITION);
                        string itemText = label.ToString().Replace("&", "");
                        uint id = GetMenuItemID(menu, i);
                        if (id != INVALID_MENU_ID && itemText.IndexOf(text, StringComparison.OrdinalIgnoreCase) >= 0) {
                            SendMessage(hWnd, WM_COMMAND, new IntPtr((long)id), IntPtr.Zero);
                            return true;
                        }
                        IntPtr submenu = GetSubMenu(menu, i);
                        if (submenu != IntPtr.Zero && SendMenuCommandByText(hWnd, submenu, text, depth + 1)) {
                            return true;
                        }
                    }
                    return false;
                }
            }
            "@

            function Quote-Argument([string]${'$'}Value) {
                return '"' + (${'$'}Value -replace '"', '\"') + '"'
            }

            if (!(Test-Path -LiteralPath ${'$'}Ppsspp)) {
                throw "PPSSPP executable does not exist: ${'$'}Ppsspp"
            }
            if (!(Test-Path -LiteralPath ${'$'}Eboot)) {
                throw "EBOOT does not exist: ${'$'}Eboot"
            }
            if (!(Test-Path -LiteralPath ${'$'}ScreenshotDirsFile)) {
                throw "Screenshot directory list does not exist: ${'$'}ScreenshotDirsFile"
            }

            ${'$'}emulatorArgs = @()
            if (Test-Path -LiteralPath ${'$'}ArgsFile) {
                ${'$'}emulatorArgs = @(Get-Content -LiteralPath ${'$'}ArgsFile | Where-Object { ${'$'}_.Length -gt 0 })
            }
            ${'$'}screenshotDirs = @(Get-Content -LiteralPath ${'$'}ScreenshotDirsFile | Where-Object { ${'$'}_.Length -gt 0 })
            foreach (${'$'}dir in ${'$'}screenshotDirs) {
                New-Item -ItemType Directory -Force -Path ${'$'}dir | Out-Null
            }
            ${'$'}captureStartedAt = [DateTime]::UtcNow
            ${'$'}allArgs = @(${'$'}emulatorArgs + @(${'$'}Eboot))
            ${'$'}argumentList = (${'$'}allArgs | ForEach-Object { Quote-Argument ${'$'}_ }) -join ' '
            Write-Host "Launching PPSSPP: ${'$'}Ppsspp ${'$'}argumentList"

            ${'$'}process = Start-Process -FilePath ${'$'}Ppsspp -ArgumentList ${'$'}argumentList -WorkingDirectory (Split-Path -Parent ${'$'}Eboot) -PassThru
            try {
                ${'$'}deadline = (Get-Date).AddSeconds(30)
                ${'$'}handle = [IntPtr]::Zero
                while ((Get-Date) -lt ${'$'}deadline) {
                    ${'$'}process.Refresh()
                    if (${'$'}process.HasExited) {
                        throw "PPSSPP exited before creating a window."
                    }
                    if (${'$'}process.MainWindowHandle -ne [IntPtr]::Zero) {
                        ${'$'}handle = ${'$'}process.MainWindowHandle
                        break
                    }
                    Start-Sleep -Milliseconds 250
                }
                if (${'$'}handle -eq [IntPtr]::Zero) {
                    throw "PPSSPP main window was not found within 30 seconds."
                }

                [LibfdxWindowCapture]::ShowWindow(${'$'}handle, 9) | Out-Null
                [LibfdxWindowCapture]::SetForegroundWindow(${'$'}handle) | Out-Null
                ${'$'}shell = New-Object -ComObject WScript.Shell
                ${'$'}activated = ${'$'}false
                for (${'$'}attempt = 0; ${'$'}attempt -lt 20; ${'$'}attempt++) {
                    if (${'$'}shell.AppActivate(${'$'}process.Id)) {
                        ${'$'}activated = ${'$'}true
                        break
                    }
                    Start-Sleep -Milliseconds 250
                }
                if (!${'$'}activated) {
                    [LibfdxWindowCapture]::SetForegroundWindow(${'$'}handle) | Out-Null
                }
                Start-Sleep -Seconds ${'$'}DelaySeconds

                ${'$'}process.Refresh()
                if (${'$'}process.HasExited) {
                    throw "PPSSPP exited before capture."
                }

                New-Item -ItemType Directory -Force -Path (Split-Path -Parent ${'$'}Capture) | Out-Null
                Remove-Item -LiteralPath ${'$'}Capture -ErrorAction SilentlyContinue
                ${'$'}screenshot = ${'$'}null
                for (${'$'}screenshotAttempt = 1; ${'$'}screenshotAttempt -le 3 -and ${'$'}screenshot -eq ${'$'}null; ${'$'}screenshotAttempt++) {
                    [LibfdxWindowCapture]::SetForegroundWindow(${'$'}handle) | Out-Null
                    ${'$'}shell.AppActivate(${'$'}process.Id) | Out-Null
                    Start-Sleep -Seconds 1
                    Write-Host "Requesting PPSSPP screenshot, attempt ${'$'}screenshotAttempt"
                    ${'$'}menuCommandSent = [LibfdxWindowCapture]::SendMenuCommandByText(${'$'}handle, "Take Screenshot")
                    if (${'$'}menuCommandSent) {
                        Write-Host "Sent PPSSPP Take Screenshot menu command"
                    }
                    else {
                        Write-Host "PPSSPP Take Screenshot menu command was not found"
                    }
                    Start-Sleep -Milliseconds 500
                    ${'$'}shell.SendKeys("{F12}")
                    Start-Sleep -Milliseconds 500
                    [LibfdxWindowCapture]::keybd_event(0x7B, 0, 0, [UIntPtr]::Zero)
                    Start-Sleep -Milliseconds 100
                    [LibfdxWindowCapture]::keybd_event(0x7B, 0, 2, [UIntPtr]::Zero)
                    Write-Host "Sent PPSSPP F12 screenshot key"
                    ${'$'}deadline = (Get-Date).AddSeconds(5)
                    while ((Get-Date) -lt ${'$'}deadline -and ${'$'}screenshot -eq ${'$'}null) {
                        ${'$'}matches = @()
                        foreach (${'$'}dir in ${'$'}screenshotDirs) {
                            if (Test-Path -LiteralPath ${'$'}dir) {
                                foreach (${'$'}pattern in @('*.png', '*.jpg', '*.jpeg', '*.bmp')) {
                                    ${'$'}matches += @(Get-ChildItem -LiteralPath ${'$'}dir -Filter ${'$'}pattern -File -ErrorAction SilentlyContinue |
                                        Where-Object { ${'$'}_.LastWriteTimeUtc -ge ${'$'}captureStartedAt })
                                }
                            }
                        }
                        ${'$'}screenshot = ${'$'}matches | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
                        if (${'$'}screenshot -eq ${'$'}null) {
                            Start-Sleep -Milliseconds 250
                        }
                    }
                }
                if (${'$'}screenshot -eq ${'$'}null) {
                    Write-Host "PPSSPP did not write a screenshot during the first wait; checking for a delayed screenshot. Watched directories: ${'$'}(${'$'}screenshotDirs -join ', ')"
                    ${'$'}lateDeadline = (Get-Date).AddSeconds(8)
                    ${'$'}lateScreenshot = ${'$'}null
                    while ((Get-Date) -lt ${'$'}lateDeadline -and ${'$'}lateScreenshot -eq ${'$'}null) {
                        ${'$'}lateMatches = @()
                        foreach (${'$'}dir in ${'$'}screenshotDirs) {
                            if (Test-Path -LiteralPath ${'$'}dir) {
                                foreach (${'$'}pattern in @('*.png', '*.jpg', '*.jpeg', '*.bmp')) {
                                    ${'$'}lateMatches += @(Get-ChildItem -LiteralPath ${'$'}dir -Filter ${'$'}pattern -File -ErrorAction SilentlyContinue |
                                        Where-Object { ${'$'}_.LastWriteTimeUtc -ge ${'$'}captureStartedAt })
                                }
                            }
                        }
                        ${'$'}lateScreenshot = ${'$'}lateMatches | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
                        if (${'$'}lateScreenshot -eq ${'$'}null) {
                            Start-Sleep -Milliseconds 250
                        }
                    }
                    if (${'$'}lateScreenshot -ne ${'$'}null) {
                        Copy-Item -LiteralPath ${'$'}lateScreenshot.FullName -Destination ${'$'}Capture -Force
                        Write-Host "Captured delayed PPSSPP screenshot: ${'$'}Capture"
                    }
                    else {
                        Write-Host "No PPSSPP screenshot was written; falling back to client-area capture"
                        ${'$'}rect = New-Object LibfdxWindowCapture+RECT
                        if (![LibfdxWindowCapture]::GetClientRect(${'$'}handle, [ref]${'$'}rect)) {
                            throw "Could not read PPSSPP client rectangle."
                        }
                        ${'$'}origin = New-Object LibfdxWindowCapture+POINT
                        ${'$'}origin.X = 0
                        ${'$'}origin.Y = 0
                        if (![LibfdxWindowCapture]::ClientToScreen(${'$'}handle, [ref]${'$'}origin)) {
                            throw "Could not convert PPSSPP client origin to screen coordinates."
                        }
                        ${'$'}width = ${'$'}rect.Right - ${'$'}rect.Left
                        ${'$'}height = ${'$'}rect.Bottom - ${'$'}rect.Top
                        if (${'$'}width -le 0 -or ${'$'}height -le 0) {
                            throw "Invalid PPSSPP window size: ${'$'}width x ${'$'}height"
                        }
                        ${'$'}bitmap = New-Object System.Drawing.Bitmap(${'$'}width, ${'$'}height)
                        ${'$'}graphics = [System.Drawing.Graphics]::FromImage(${'$'}bitmap)
                        try {
                            ${'$'}graphics.CopyFromScreen(${'$'}origin.X, ${'$'}origin.Y, 0, 0, ${'$'}bitmap.Size)
                            ${'$'}bitmap.Save(${'$'}Capture, [System.Drawing.Imaging.ImageFormat]::Png)
                        }
                        catch {
                            throw "PPSSPP did not write a screenshot and client-area capture failed: ${'$'}(${'$'}_.Exception.Message)"
                        }
                        finally {
                            ${'$'}graphics.Dispose()
                            ${'$'}bitmap.Dispose()
                        }
                        Write-Host "Captured PPSSPP client area: ${'$'}Capture"
                    }
                }
                else {
                    Copy-Item -LiteralPath ${'$'}screenshot.FullName -Destination ${'$'}Capture -Force
                    Write-Host "Captured PPSSPP screenshot: ${'$'}Capture"
                }
            }
            finally {
                ${'$'}process.Refresh()
                if (!${'$'}process.HasExited) {
                    if (${'$'}process.CloseMainWindow()) {
                        if (!${'$'}process.WaitForExit(2000)) {
                            ${'$'}process.Kill()
                        }
                    }
                    else {
                        ${'$'}process.Kill()
                    }
                }
            }
            """.trimIndent(), Charsets.UTF_8)
        return script
    }

    private fun writeCaptureConfig(): File {
        val config = File(temporaryDir, "ppsspp-capture.ini")
        config.writeText("""
            [General]
            FirstRun = False
            CheckForNewVersion = False
            UISound = False
            AutoRun = True
            ShowMenuBar = True
            WindowWidth = 960
            WindowHeight = 544
            WindowSizeState = 0
            TransparentBackground = False
            ScreenshotsAsPNG = True

            [Graphics]
            iShowStatusFlags = 0

            [Control]
            ShowTouchControls = False
            """.trimIndent(), Charsets.UTF_8)
        return config
    }

    private fun ppssppScreenshotDirectories(executable: File): List<File> {
        return ppssppMemstickRoots(executable)
            .map { File(it, "PSP/SCREENSHOT") }
            .distinctBy { it.absolutePath }
    }

    private fun ppssppMemstickRoots(executable: File): List<File> {
        val userHome = File(System.getProperty("user.home"))
        return listOf(
            File(executable.parentFile, "memstick"),
            File(userHome, "Documents/PPSSPP"),
            File(userHome, "OneDrive/Documents/PPSSPP"),
            File(userHome, "PPSSPP")
        ).distinctBy { it.absolutePath }
    }

    private companion object {
        val PPSSPP_EXECUTABLE_NAMES = setOf("PPSSPPWindows64.exe", "PPSSPP.exe", "ppsspp.exe")
    }
}

abstract class LibfdxDesktopCRunTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val releaseDir: DirectoryProperty

    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val buildType: Property<String>

    @get:Input
    abstract val openConsole: Property<Boolean>

    @get:Input
    abstract val runArgs: ListProperty<String>

    @TaskAction
    fun run() {
        val suffix = if(buildType.get().equals("release", ignoreCase = true)) "release" else "debug"
        val executable = File(releaseDir.get().asFile, projectName.get() + "_" + suffix + if(isWindows()) ".exe" else "")
        if(!executable.isFile) {
            throw IllegalStateException("Native executable was not built: ${executable.absolutePath}")
        }
        val command = mutableListOf(executable.absolutePath)
        command.addAll(runArgs.get())
        val workingDirectory = releaseDir.get().asFile
        val processCommand = if(isWindows() && openConsole.get()) {
            windowsPowerShellStartCommand(executable, runArgs.get(), workingDirectory)
        }
        else {
            command
        }
        val process = ProcessBuilder(processCommand)
            .directory(workingDirectory)
            .inheritIO()
            .start()
        val exitCode = process.waitFor()
        if(exitCode != 0) {
            throw IllegalStateException("Native executable failed with exit code $exitCode")
        }
    }
}

private fun isWindows(): Boolean {
    return System.getProperty("os.name").lowercase().contains("windows")
}

private fun deleteDirectory(path: File) {
    if(!path.exists()) {
        return
    }
    path.walkBottomUp().forEach { file ->
        if(!file.delete() && file.exists()) {
            throw IllegalStateException("Could not delete ${file.absolutePath}")
        }
    }
}

private fun copyDirectoryContents(sourceRoot: File, outputRoot: File) {
    val normalizedSourceRoot = sourceRoot.canonicalFile.toPath()
    val normalizedOutputRoot = outputRoot.canonicalFile.toPath()
    sourceRoot.walkTopDown()
        .filter { it.isFile }
        .forEach { source ->
            val relative = normalizedSourceRoot.relativize(source.canonicalFile.toPath())
            val output = normalizedOutputRoot.resolve(relative).normalize()
            if(!output.startsWith(normalizedOutputRoot)) {
                throw IllegalStateException("Refusing to copy asset outside output directory: ${source.absolutePath}")
            }
            Files.createDirectories(output.parent)
            Files.copy(source.toPath(), output, StandardCopyOption.REPLACE_EXISTING)
        }
}

private fun copyAssetRoots(assetRoots: Iterable<File>, outputRoot: File) {
    deleteDirectory(outputRoot)
    Files.createDirectories(outputRoot.toPath())
    assetRoots.forEach { asset ->
        when {
            asset.isDirectory -> copyDirectoryContents(asset, outputRoot)
            asset.isFile -> {
                val output = File(outputRoot, asset.name)
                Files.copy(asset.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

private fun windowsPowerShellStartCommand(executable: File, args: List<String>, workingDirectory: File): List<String> {
    val argumentList = args.joinToString(", ") { powershellSingleQuoted(it) }
    val script = "${'$'}p = Start-Process -FilePath ${powershellSingleQuoted(executable.absolutePath)} " +
            "-ArgumentList @($argumentList) " +
            "-WorkingDirectory ${powershellSingleQuoted(workingDirectory.absolutePath)} " +
            "-Wait -PassThru; exit ${'$'}p.ExitCode"
    return listOf("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script)
}

private fun powershellSingleQuoted(value: String): String {
    return "'" + value.replace("'", "''") + "'"
}
