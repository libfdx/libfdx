package io.github.libfdx.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.getByType
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Properties

/** Opt-in runner for discovered preview entries or an explicitly configured Java main. */
open class LibfdxUiPreviewExtension(project: Project, objects: ObjectFactory) {
    val mainClass: Property<String> = objects.property(String::class.java)
    val workingDir = objects.directoryProperty().convention(project.rootProject.layout.projectDirectory)
    val runtimeClasspath: ConfigurableFileCollection = project.files()
    val watch: ConfigurableFileCollection = project.files()
    val javaLanguageVersion = objects.property(Int::class.java).convention(25)
    val jvmArgs = objects.listProperty(String::class.java).convention(
        listOf("--enable-native-access=ALL-UNNAMED", "-Dorg.lwjgl.system.stackSize=1024"))
    val arguments = objects.listProperty(String::class.java).convention(emptyList())
    val systemProperties = objects.mapProperty(String::class.java, String::class.java).convention(emptyMap())
    val buildArguments = objects.listProperty(String::class.java).convention(emptyList())
    val fastReload = objects.property(Boolean::class.java).convention(true)
    val pollMillis = objects.property(Long::class.java).convention(50L)
    val debounceMillis = objects.property(Long::class.java).convention(120L)
    val startupTimeoutMillis = objects.property(Long::class.java).convention(30_000L)
}

internal const val UI_PREVIEW_PREPARE = "libfdx_ui_preview_prepare"
internal const val UI_PREVIEW_RUN = "libfdx_ui_preview_run"
internal const val UI_PREVIEW_STOP = "libfdx_ui_preview_stop"

internal fun registerUiPreviewTasks(project: Project, extension: LibfdxExtension, tool: Configuration) {
    val preview = extension.uiPreview
    val sources = project.extensions.getByType<SourceSetContainer>().getByName("main")
    // API types are available to ordinary main sources; tooling is absent from application runtime exports.
    project.configurations.getByName("compileOnly").extendsFrom(tool)
    val classpath = preview.runtimeClasspath + sources.runtimeClasspath + tool
    val toolchains = project.extensions.getByType<JavaToolchainService>()
    val launcher = toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(preview.javaLanguageVersion.get())) }
    val controllerClasspath = tool.elements.map { entries ->
        entries.map { it.asFile }.firstOrNull { it.name.startsWith("tools_ui_preview") && it.extension == "jar" }
            ?: throw GradleException("Cannot locate the tools_ui_preview SDK jar.")
    }
    val modelFile = project.layout.buildDirectory.file("ui-preview/launch.properties")
    val session = project.layout.buildDirectory.dir("ui-preview/session")
    val prepare = project.tasks.register(UI_PREVIEW_PREPARE) {
        notCompatibleWithConfigurationCache("UI preview prepares live project classpaths and watched source roots.")
        group = "libfdx"
        description = "Compiles existing preview/main sources and writes data-only preview launch settings."
        dependsOn("classes", classpath)
        outputs.file(modelFile)
        outputs.upToDateWhen { false }
        doLast {
            val root = project.rootProject.projectDir
            if (!root.resolve("gradle/wrapper/gradle-wrapper.jar").isFile) {
                throw GradleException("UI preview automatic builds require the project's Gradle wrapper jar.")
            }
            val properties = Properties()
            properties.setProperty("formatVersion", "1")
            properties.setProperty("mainClass", preview.mainClass.orElse("").get())
            properties.setProperty("javaExecutable", launcher.get().executablePath.asFile.absolutePath)
            properties.setProperty("controllerClasspath", controllerClasspath.get().absolutePath)
            properties.setProperty("projectDirectory", root.absolutePath)
            properties.setProperty("workingDirectory", preview.workingDir.get().asFile.absolutePath)
            properties.setProperty("sessionDirectory", session.get().asFile.absolutePath)
            properties.setProperty("prepareTask", path)
            properties.setProperty("pollMillis", preview.pollMillis.get().toString())
            properties.setProperty("debounceMillis", preview.debounceMillis.get().toString())
            properties.setProperty("startupTimeoutMillis", preview.startupTimeoutMillis.get().toString())
            val paths = classpath.files.toList()
            previewValues(properties, "classpath", paths.map(File::getAbsolutePath))
            val shared = tool.files.toSet()
            properties.setProperty("fastReload", preview.fastReload.get().toString())
            val projectOutputs = previewJavaModel(project, properties, shared, paths)
            val cache = project.gradle.gradleUserHomeDir.resolve("caches").toPath().toAbsolutePath().normalize()
            properties.setProperty("cacheDirectory", cache.toString())
            previewValues(properties, "reloadClasspath", paths.filter {
                val path = it.toPath().toAbsolutePath().normalize()
                it !in shared && !path.startsWith(cache) && projectOutputs.any(path::startsWith)
            }.map(File::getAbsolutePath))
            paths.forEachIndexed { index, file ->
                properties.setProperty("classpath.$index.immutable", file.toPath().toAbsolutePath().normalize().startsWith(cache).toString())
            }
            previewValues(properties, "assets", extension.assets.files.map(File::getAbsolutePath))
            previewValues(properties, "arguments", preview.arguments.get())
            val forwarded = project.gradle.startParameter.systemPropertiesArgs.filterKeys { it.startsWith("libfdx.uiPreview.") }
            previewValues(properties, "jvmArguments", preview.jvmArgs.get() +
                (preview.systemProperties.get() + forwarded).map { (key, value) -> "-D$key=$value" })
            val buildArgs = mutableListOf("--console=plain", "--max-workers=${project.gradle.startParameter.maxWorkerCount}")
            if (project.gradle.startParameter.isOffline) buildArgs.add("--offline")
            buildArgs.add("--gradle-user-home"); buildArgs.add(project.gradle.gradleUserHomeDir.absolutePath)
            project.gradle.startParameter.projectProperties.forEach { (key, value) -> buildArgs.add("-P$key=$value") }
            forwarded.forEach { (key, value) -> buildArgs.add("-D$key=$value") }
            buildArgs.addAll(preview.buildArguments.get())
            previewValues(properties, "buildArguments", buildArgs)
            previewValues(properties, "watch", previewWatchRoots(project, extension, preview).map(File::getAbsolutePath))
            val file = modelFile.get().asFile
            file.parentFile.mkdirs()
            val temporary = Files.createTempFile(file.parentFile.toPath(), "launch-", ".tmp")
            try {
                Files.newBufferedWriter(temporary, Charsets.UTF_8).use { properties.store(it, null) }
                try {
                    Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }
    project.tasks.register(UI_PREVIEW_RUN) {
        notCompatibleWithConfigurationCache("UI preview starts an external development process.")
        group = "libfdx"
        description = "Opens the direct-call UI preview and watches/rebuilds it after edits; returns while the window stays open."
        dependsOn(prepare)
        doLast {
            val directory = session.get().asFile.toPath()
            Files.createDirectories(directory)
            FileChannel.open(directory.resolve("controller.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
                val lock = channel.tryLock() ?: throw GradleException("UI preview is already running. Close its window or run $UI_PREVIEW_STOP.")
                lock.release()
            }
            // The supervisor uses JDK APIs. A private copy isolates it from SDK rebuilds.
            val controllerJar = directory.resolve("controller.jar")
            Files.copy(controllerClasspath.get().toPath(), controllerJar, StandardCopyOption.REPLACE_EXISTING)
            Files.deleteIfExists(directory.resolve("controller.ready"))
            val process = ProcessBuilder(launcher.get().executablePath.asFile.absolutePath,
                "-classpath", controllerJar.toString(), "io.github.libfdx.tools.uipreview.UiPreviewWatch",
                modelFile.get().asFile.absolutePath)
                .directory(project.rootProject.projectDir).redirectErrorStream(true)
                .redirectOutput(directory.resolve("watch.log").toFile()).start()
            val deadline = System.nanoTime() + 15_000_000_000L
            var ready = false
            while (process.isAlive && System.nanoTime() < deadline) {
                val marker = directory.resolve("controller.ready")
                if (Files.isRegularFile(marker) && Files.readString(marker).trim() == process.pid().toString()) {
                    ready = true; break
                }
                Thread.sleep(50)
            }
            if (!ready) {
                process.destroy()
                throw GradleException("Preview supervisor did not start. See ${directory.resolve("watch.log")}")
            }
            logger.lifecycle("UI preview supervisor started (PID ${process.pid()}). Logs/status: $directory")
            logger.lifecycle("Close the preview window or run $UI_PREVIEW_STOP to stop automatic rebuilds.")
        }
    }
    project.tasks.register(UI_PREVIEW_STOP) {
        notCompatibleWithConfigurationCache("UI preview controls an external development process.")
        group = "libfdx"
        description = "Stops this module's UI preview watcher and its owned preview processes."
        doLast {
            val directory = session.get().asFile.toPath()
            Files.createDirectories(directory)
            Files.writeString(directory.resolve("stop.request"), "stop\n")
            val deadline = System.nanoTime() + 15_000_000_000L
            while (Files.exists(directory.resolve("controller.ready")) && System.nanoTime() < deadline) Thread.sleep(100)
            if (Files.exists(directory.resolve("controller.ready"))) throw GradleException("Preview stop timed out; inspect $directory")
            logger.lifecycle("UI preview stopped.")
        }
    }
}

internal fun previewValues(properties: Properties, key: String, values: List<String>) {
    properties.setProperty("$key.count", values.size.toString())
    values.forEachIndexed { index, value -> properties.setProperty("$key.$index", value) }
}

private fun previewWatchRoots(project: Project, extension: LibfdxExtension, preview: LibfdxUiPreviewExtension): Set<File> {
    val roots = linkedSetOf<File>()
    val visited = hashSetOf<String>()
    fun visit(current: Project) {
        if (!visited.add(current.path)) return
        val build = current.layout.buildDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        current.extensions.findByType(SourceSetContainer::class.java)?.findByName("main")?.allSource?.srcDirs
            ?.filter { !it.toPath().toAbsolutePath().normalize().startsWith(build) }?.let { roots.addAll(it) }
        roots.add(current.buildFile)
        current.configurations.findByName("runtimeClasspath")?.allDependencies?.withType(ProjectDependency::class.java)?.forEach {
            current.rootProject.findProject(it.path)?.let { dependency -> visit(dependency) }
        }
    }
    visit(project)
    roots.addAll(extension.assets.files)
    roots.addAll(preview.watch.files)
    roots.add(project.rootProject.buildFile)
    listOf("settings.gradle", "settings.gradle.kts", "gradle.properties", "local.properties", "gradle/libs.versions.toml").forEach {
        roots.add(project.rootProject.file(it))
    }
    return roots
}
