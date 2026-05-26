package io.github.libfdx.gradle

import io.github.libfdx.backend.web.TeaVMAssetProperties
import io.github.libfdx.backend.web.WebAsset
import io.github.libfdx.backend.web.WebAssets
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.diagnostics.TaskReportTask
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.teavm.gradle.TeaVMPlugin
import org.teavm.gradle.api.TeaVMExtension
import org.teavm.gradle.tasks.GenerateCTask

class LibfdxGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(JavaPlugin::class.java)
        project.pluginManager.apply(TeaVMPlugin::class.java)
        hideTeaVMTasks(project)
        hideTeaVMTasksFromTaskReport(project)

        val teavm = project.extensions.getByType<TeaVMExtension>()
        val extension = project.extensions.create<LibfdxExtension>(
            "libfdx",
            project,
            teavm.getJs(),
            teavm.getWasmGC(),
            teavm.getC()
        )

        project.afterEvaluate {
            registerBitmapFontTasks(project, extension)
            configureTargets(project, extension)
            registerTasks(project, extension)
        }
    }

    private fun configureTargets(project: Project, extension: LibfdxExtension) {
        validateNativeCTargets(extension)
        if(extension.isDeclared(LibfdxTarget.JS) || extension.isDeclared(LibfdxTarget.WASM)) {
            configureWebAssets(extension)
        }
        if(extension.isDeclared(LibfdxTarget.DESKTOP_NATIVE)) {
            configureDesktopNative(project, extension)
        }
        if(extension.isDeclared(LibfdxTarget.PSP)) {
            configurePsp(project, extension)
        }
    }

    private fun validateNativeCTargets(extension: LibfdxExtension) {
        val nativeTargets = listOf(LibfdxTarget.DESKTOP_NATIVE, LibfdxTarget.PSP)
            .filter { extension.isDeclared(it) }
        if(nativeTargets.size > 1) {
            throw GradleException("Declare only one libfdx TeaVM C target in a project for now: desktopNative or psp.")
        }
    }

    private fun configureWebAssets(extension: LibfdxExtension) {
        val entries = WebAssets.collect(extension.assets.files.map { it.toPath() })
        if(extension.isDeclared(LibfdxTarget.JS)) {
            configureWebAssetProperties(extension.js.teavmConfig.properties, entries)
        }
        if(extension.isDeclared(LibfdxTarget.WASM)) {
            configureWebAssetProperties(extension.wasm.teavmConfig.properties, entries)
        }
    }

    private fun configureWebAssetProperties(properties: MapProperty<String, String>, entries: List<WebAsset>) {
        properties.put(TeaVMAssetProperties.COUNT_PROPERTY, entries.size.toString())
        entries.forEachIndexed { index, entry ->
            properties.put("${TeaVMAssetProperties.ENTRY_PROPERTY_PREFIX}$index.path", entry.path)
            properties.put("${TeaVMAssetProperties.ENTRY_PROPERTY_PREFIX}$index.size", entry.size.toString())
        }
    }

    private fun configureDesktopNative(project: Project, extension: LibfdxExtension) {
        val desktopNative = extension.desktopNative
        project.tasks.named(TeaVMPlugin.C_TASK_NAME, GenerateCTask::class.java).configure {
            targetFileName.set(desktopNative.targetFileName)
            properties.put("libfdx.native.backend", "desktop_native")
        }
    }

    private fun configurePsp(project: Project, extension: LibfdxExtension) {
        val psp = extension.psp
        project.tasks.named(TeaVMPlugin.C_TASK_NAME, GenerateCTask::class.java).configure {
            targetFileName.set(psp.targetFileName)
            properties.put("libfdx.native.backend", "psp")
        }
    }

    private fun registerTasks(project: Project, extension: LibfdxExtension) {
        if(extension.isDeclared(LibfdxTarget.JS)) {
            registerJsTasks(project, extension)
        }
        if(extension.isDeclared(LibfdxTarget.WASM)) {
            registerWasmTasks(project, extension)
        }
        if(extension.isDeclared(LibfdxTarget.DESKTOP_NATIVE)) {
            registerDesktopNativeTasks(project, extension)
        }
        if(extension.isDeclared(LibfdxTarget.PSP)) {
            registerPspTasks(project, extension)
        }
    }

    private fun registerBitmapFontTasks(project: Project, extension: LibfdxExtension) {
        val fontTasks = mutableListOf<TaskProvider<LibfdxBitmapFontTask>>()
        extension.bitmapFonts.forEach { bitmapFont ->
            val safeName = safeTaskName(bitmapFont.name)
            val defaultOutput = project.layout.buildDirectory.dir("generated/libfdx/bitmap-fonts/$safeName")
            fontTasks.add(project.tasks.register<LibfdxBitmapFontTask>("libfdx_bitmap_font_$safeName") {
                group = TASK_GROUP
                description = "Generate the '${bitmapFont.name}' libfdx bitmap font."
                fontName.set(bitmapFont.name)
                sourceFile.set(bitmapFont.sourceFile)
                assetPath.set(bitmapFont.assetPath)
                size.set(bitmapFont.size)
                padding.set(bitmapFont.padding)
                maxTextureSize.set(bitmapFont.maxTextureSize)
                characters.set(bitmapFont.characters)
                outputDir.set(bitmapFont.outputDir.orElse(defaultOutput))
            })
        }
        project.tasks.register("libfdx_generate_bitmap_fonts") {
            group = TASK_GROUP
            description = "Generate configured libfdx bitmap fonts."
            dependsOn(fontTasks)
        }
    }

    private fun registerJsTasks(project: Project, extension: LibfdxExtension) {
        val runtimeClasspath = project.extensions.getByType<SourceSetContainer>().getByName("main").runtimeClasspath
        val runtimeCore = project.rootProject.findProject(":libfdx:runtime:core")
        val runtimeCoreWebResources = runtimeCore?.tasks?.named("build_web_freetype_emscripten")
        val runtimeCoreWebResourcesDir = runtimeCore?.layout?.buildDirectory?.dir("generated/resources/runtimeCoreWeb")
        val prepare = project.tasks.register<LibfdxWebAppTask>("libfdx_web_js_prepare") {
            group = TASK_GROUP
            description = "Generate the libfdx Web JavaScript web application shell."
            dependsOn(project.tasks.named(TeaVMPlugin.JS_TASK_NAME))
            runtimeCoreWebResources?.let { dependsOn(it) }
            webappDir.set(extension.js.webappDir())
            title.set(extension.js.htmlTitle)
            width.set(extension.js.htmlWidth)
            height.set(extension.js.htmlHeight)
            canvasId.set(extension.js.canvasId)
            entryPointName.set(extension.js.entryPointName)
            mainClassArgs.set(extension.js.mainClassArgs)
            targetFileName.set(extension.js.targetFileName)
            wasm.set(false)
            assets.from(extension.assets)
            this.runtimeClasspath.from(runtimeClasspath)
            runtimeCoreWebResourcesDir?.let { this.runtimeClasspath.from(it) }
        }
        val build = project.tasks.register("libfdx_web_js_build") {
            group = TASK_GROUP
            description = "Build the libfdx Web JavaScript web application."
            dependsOn(prepare)
        }
        project.tasks.register<LibfdxRunWebTask>("libfdx_web_js_run") {
            group = TASK_GROUP
            description = "Build and serve the libfdx Web JavaScript web application."
            dependsOn(build)
            webappDir.set(extension.js.webappDir())
            port.set(extension.js.serverPort)
            defaultPath.set("/")
        }
    }

    private fun registerWasmTasks(project: Project, extension: LibfdxExtension) {
        val runtimeClasspath = project.extensions.getByType<SourceSetContainer>().getByName("main").runtimeClasspath
        val runtimeCore = project.rootProject.findProject(":libfdx:runtime:core")
        val runtimeCoreWebResources = runtimeCore?.tasks?.named("build_web_freetype_emscripten")
        val runtimeCoreWebResourcesDir = runtimeCore?.layout?.buildDirectory?.dir("generated/resources/runtimeCoreWeb")
        val prepare = project.tasks.register<LibfdxWebAppTask>("libfdx_web_wasm_prepare") {
            group = TASK_GROUP
            description = "Generate the libfdx Web Wasm web application shell."
            dependsOn(project.tasks.named(TeaVMPlugin.BUILD_WASM_GC_TASK_NAME))
            runtimeCoreWebResources?.let { dependsOn(it) }
            webappDir.set(extension.wasm.webappDir())
            title.set(extension.wasm.htmlTitle)
            width.set(extension.wasm.htmlWidth)
            height.set(extension.wasm.htmlHeight)
            canvasId.set(extension.wasm.canvasId)
            entryPointName.set(extension.wasm.entryPointName)
            mainClassArgs.set(extension.wasm.mainClassArgs)
            targetFileName.set(extension.wasm.targetFileName)
            wasm.set(true)
            assets.from(extension.assets)
            this.runtimeClasspath.from(runtimeClasspath)
            runtimeCoreWebResourcesDir?.let { this.runtimeClasspath.from(it) }
        }
        val build = project.tasks.register("libfdx_web_wasm_build") {
            group = TASK_GROUP
            description = "Build the libfdx Web Wasm web application."
            dependsOn(prepare)
        }
        project.tasks.register<LibfdxRunWebTask>("libfdx_web_wasm_run") {
            group = TASK_GROUP
            description = "Build and serve the libfdx Web Wasm web application."
            dependsOn(build)
            webappDir.set(extension.wasm.webappDir())
            port.set(extension.wasm.serverPort)
            defaultPath.set("/")
        }
    }

    private fun registerDesktopNativeTasks(project: Project, extension: LibfdxExtension) {
        val sourceSets = project.extensions.getByType<SourceSetContainer>()
        val runtimeClasspath = sourceSets.getByName("main").runtimeClasspath
        val generate = project.tasks.register<LibfdxDesktopNativeProjectTask>("libfdx_desktop_native_generate") {
            group = TASK_GROUP
            description = "Generate the libfdx desktop_native project."
            dependsOn(project.tasks.named(TeaVMPlugin.C_TASK_NAME))
            buildRoot.set(extension.desktopNative.outputDir)
            generatedSourcesDir.set(extension.desktopNative.generatedSourcesDir())
            releaseDir.set(extension.desktopNative.releasePath)
            projectName.set(extension.desktopNative.targetFileName)
            buildType.set(extension.desktopNative.buildType)
            showConsole.set(extension.desktopNative.showConsole)
            nativeResourceClasspath.from(runtimeClasspath)
        }
        val buildDebug = project.tasks.register<LibfdxNativeBuildTask>("libfdx_desktop_native_build_debug") {
            group = TASK_GROUP
            description = "Generate and build the libfdx desktop_native Debug executable."
            dependsOn(generate)
            buildRoot.set(extension.desktopNative.outputDir)
            scriptBaseName.set("app_debug")
        }
        val buildRelease = project.tasks.register<LibfdxNativeBuildTask>("libfdx_desktop_native_build_release") {
            group = TASK_GROUP
            description = "Generate and build the libfdx desktop_native Release executable."
            dependsOn(generate)
            buildRoot.set(extension.desktopNative.outputDir)
            scriptBaseName.set("app_release")
        }
        project.tasks.register<LibfdxDesktopNativeRunTask>("libfdx_desktop_native_run_debug") {
            group = TASK_GROUP
            description = "Generate, build, and run the libfdx desktop_native Debug executable."
            dependsOn(buildDebug)
            releaseDir.set(extension.desktopNative.releasePath)
            projectName.set(extension.desktopNative.targetFileName)
            buildType.set("Debug")
            openConsole.set(extension.desktopNative.openConsole)
            runArgs.set(project.providers.gradleProperty("libfdx.desktopNative.runArgs").map { value ->
                value.split(Regex("\\s+")).filter { it.isNotBlank() }
            }.orElse(emptyList()))
        }
        project.tasks.register<LibfdxDesktopNativeRunTask>("libfdx_desktop_native_run_release") {
            group = TASK_GROUP
            description = "Generate, build, and run the libfdx desktop_native Release executable."
            dependsOn(buildRelease)
            releaseDir.set(extension.desktopNative.releasePath)
            projectName.set(extension.desktopNative.targetFileName)
            buildType.set("Release")
            openConsole.set(extension.desktopNative.openConsole)
            runArgs.set(project.providers.gradleProperty("libfdx.desktopNative.runArgs").map { value ->
                value.split(Regex("\\s+")).filter { it.isNotBlank() }
            }.orElse(emptyList()))
        }
    }

    private fun registerPspTasks(project: Project, extension: LibfdxExtension) {
        val sourceSets = project.extensions.getByType<SourceSetContainer>()
        val runtimeClasspath = sourceSets.getByName("main").runtimeClasspath
        val generate = project.tasks.register<LibfdxPspProjectTask>("libfdx_psp_generate") {
            group = TASK_GROUP
            description = "Generate the libfdx PSP project."
            dependsOn(project.tasks.named(TeaVMPlugin.C_TASK_NAME))
            buildRoot.set(extension.psp.outputDir)
            generatedSourcesDir.set(extension.psp.generatedSourcesDir())
            releaseDir.set(extension.psp.releasePath)
            projectName.set(extension.psp.targetFileName)
            debugMemory.set(extension.psp.debugMemory)
            nativeResourceClasspath.from(runtimeClasspath)
            assets.from(extension.assets)
        }
        val build = project.tasks.register<LibfdxNativeBuildTask>("libfdx_psp_build") {
            group = TASK_GROUP
            description = "Generate and build the libfdx PSP EBOOT project."
            dependsOn(generate)
            buildRoot.set(extension.psp.outputDir)
            scriptBaseName.set("build")
        }
        project.tasks.register<LibfdxPspPpssppCaptureTask>("libfdx_psp_ppsspp_capture") {
            group = TASK_GROUP
            description = "Build the libfdx PSP EBOOT and capture a PPSSPP emulator frame."
            dependsOn(build)
            releaseDir.set(extension.psp.releasePath)
            projectName.set(extension.psp.targetFileName)
            ppssppExecutable.set(extension.psp.ppssppExecutable)
            captureDelaySeconds.set(extension.psp.ppssppCaptureDelaySeconds)
            ppssppAutoDownload.set(extension.psp.ppssppAutoDownload)
            ppssppDownloadUrl.set(extension.psp.ppssppDownloadUrl)
            ppssppToolDir.set(project.layout.buildDirectory.dir("tools/ppsspp"))
            emulatorArgs.set(listOf("--windowed", "--escape-exit"))
            captureFile.set(project.layout.buildDirectory.file(extension.psp.targetFileName.map { name ->
                "reports/ppsspp/$name.png"
            }))
        }
    }

    private fun hideTeaVMTasks(project: Project) {
        project.tasks.configureEach {
            if(isTeaVMTask()) {
                group = null
            }
        }
        project.afterEvaluate {
            tasks.configureEach {
                if(isTeaVMTask()) {
                    group = null
                }
            }
        }
    }

    private fun org.gradle.api.Task.isTeaVMTask(): Boolean {
        return group.equals("teavm", ignoreCase = true)
                || name in TEAVM_TASK_NAMES
                || name.contains("teavm", ignoreCase = true)
    }

    private fun hideTeaVMTasksFromTaskReport(project: Project) {
        project.tasks.withType(TaskReportTask::class.java).configureEach {
            doFirst {
                displayGroups = project.tasks
                    .mapNotNull { task -> task.group }
                    .filterNot { group -> group.equals("teavm", ignoreCase = true) }
                    .distinct()
                    .sorted()
                setShowDetail(false)
            }
        }
    }

    private fun safeTaskName(name: String): String {
        val builder = StringBuilder()
        for(character in name) {
            if(character.isLetterOrDigit()) {
                builder.append(character)
            }
            else {
                builder.append('_')
            }
        }
        return builder.toString().ifBlank { "font" }
    }

    internal companion object {
        const val TASK_GROUP = "libfdx"
        private val TEAVM_TASK_NAMES = setOf(
            TeaVMPlugin.BUILD_WASM_GC_TASK_NAME,
            TeaVMPlugin.C_TASK_NAME,
            TeaVMPlugin.JS_TASK_NAME,
            "copyWasmGCRuntime",
            "disasmWasmGC",
            "emscriptenStubWasmGC",
            "emscriptenWasmGC",
            "generateWasmGC",
            "javaScriptDevServer",
            "stopJavaScriptDevServer"
        )
    }
}
