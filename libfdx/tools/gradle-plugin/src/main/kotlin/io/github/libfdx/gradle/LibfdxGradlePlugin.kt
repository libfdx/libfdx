package io.github.libfdx.gradle

import io.github.libfdx.backend.web.TeaVMAssetProperties
import io.github.libfdx.backend.web.WebAsset
import io.github.libfdx.backend.web.WebAssets
import org.gradle.api.Action
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.diagnostics.TaskReportTask
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.teavm.gradle.TeaVMPlugin
import org.teavm.gradle.api.OptimizationLevel
import org.teavm.gradle.api.TeaVMExtension
import org.teavm.gradle.api.TeaVMCConfiguration
import org.teavm.gradle.tasks.GenerateCTask

internal fun prioritizedDesktopJvmClasspath(
    targetRuntimeClasspath: FileCollection,
    applicationRuntimeClasspath: FileCollection
): FileCollection = targetRuntimeClasspath + applicationRuntimeClasspath

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
        extension.ecsProject.projectClasses.from(
            project.extensions.getByType<SourceSetContainer>().getByName("main").output
        )

        project.afterEvaluate {
            registerBitmapFontTasks(project, extension)
            registerShaderTasks(project, extension)
            val requestedTasks = requestedTaskNames(project)
            val nativeTarget = selectedNativeTarget(extension, requestedTasks)
            configureTargets(project, extension, nativeTarget, requestedTasks)
            registerTasks(project, extension)
            project.gradle.taskGraph.whenReady(object : Action<TaskExecutionGraph> {
                override fun execute(graph: TaskExecutionGraph) {
                    val taskGraphTasks = graph.allTasks
                        .filter { task -> task.project == project }
                        .map { task -> task.name }
                        .toSet()
                    val graphNativeTarget = selectedNativeTarget(extension, taskGraphTasks)
                    validateNativeCTargets(extension, graphNativeTarget, taskGraphTasks)
                    if(graphNativeTarget == LibfdxTarget.DESKTOP_C) {
                        configureDesktopC(project, extension, taskGraphTasks)
                    }
                    if(graphNativeTarget == LibfdxTarget.PSP) {
                        configurePsp(project, extension, taskGraphTasks)
                    }
                    if(graphNativeTarget == LibfdxTarget.IOS_C) {
                        configureIosC(project, extension, taskGraphTasks)
                    }
                }
            })
        }
    }

    private fun configureTargets(
        project: Project,
        extension: LibfdxExtension,
        nativeTarget: LibfdxTarget?,
        requestedTasks: Set<String>
    ) {
        validateNativeCTargets(extension, nativeTarget, requestedTasks)
        if(extension.isDeclared(LibfdxTarget.JS) || extension.isDeclared(LibfdxTarget.WASM)) {
            configureWebAssets(extension)
        }
        if(nativeTarget == LibfdxTarget.DESKTOP_C) {
            configureDesktopC(project, extension, requestedTasks)
        }
        if(nativeTarget == LibfdxTarget.PSP) {
            configurePsp(project, extension, requestedTasks)
        }
        if(nativeTarget == LibfdxTarget.IOS_C) {
            configureIosC(project, extension, requestedTasks)
        }
    }

    private fun validateNativeCTargets(
        extension: LibfdxExtension,
        nativeTarget: LibfdxTarget?,
        requestedTasks: Set<String>
    ) {
        val nativeTargets = listOf(LibfdxTarget.DESKTOP_C, LibfdxTarget.PSP, LibfdxTarget.IOS_C)
            .filter { extension.isDeclared(it) }
        if(nativeTargets.size > 1 && nativeTarget == null) {
            throw GradleException(
                "Declare only one libfdx TeaVM C target in a project for now: desktopC, psp, or iosC."
            )
        }
        val requestedPspTargets = selectedPspTargets(extension, requestedTasks)
        if(requestedPspTargets.size > 1) {
            throw GradleException("Run PSP plugin targets in separate Gradle invocations: ${requestedPspTargets.map { it.name }}")
        }
        val requestedDesktopCTargets = selectedDesktopCTargets(extension, requestedTasks)
        if(requestedDesktopCTargets.size > 1) {
            throw GradleException(
                "Run desktop_c plugin targets in separate Gradle invocations: "
                        + requestedDesktopCTargets.map { it.name }
            )
        }
        val requestedIosCTargets = selectedIosCTargets(extension, requestedTasks)
        if(requestedIosCTargets.size > 1) {
            throw GradleException("Run ios_c plugin targets in separate Gradle invocations: "
                    + requestedIosCTargets.map { it.name })
        }
        val requestedNativeKinds = listOf(
            wantsDesktopCTarget(extension, requestedTasks),
            wantsPspTarget(extension, requestedTasks),
            wantsIosCTarget(extension, requestedTasks)
        ).count { it }
        if(requestedNativeKinds > 1) {
            throw GradleException("Run desktop_c, PSP, and ios_c plugin tasks in separate Gradle invocations.")
        }
    }

    private fun selectedNativeTarget(extension: LibfdxExtension, requestedTasks: Set<String>): LibfdxTarget? {
        val desktopCDeclared = extension.isDeclared(LibfdxTarget.DESKTOP_C)
        val pspDeclared = extension.isDeclared(LibfdxTarget.PSP)
        val iosCDeclared = extension.isDeclared(LibfdxTarget.IOS_C)
        val wantsDesktopC = wantsDesktopCTarget(extension, requestedTasks)
        val wantsPsp = wantsPspTarget(extension, requestedTasks)
        val wantsIosC = wantsIosCTarget(extension, requestedTasks)
        if(wantsDesktopC && desktopCDeclared) {
            return LibfdxTarget.DESKTOP_C
        }
        if(wantsPsp && pspDeclared) {
            return LibfdxTarget.PSP
        }
        if(wantsIosC && iosCDeclared) {
            return LibfdxTarget.IOS_C
        }
        return when {
            desktopCDeclared -> LibfdxTarget.DESKTOP_C
            pspDeclared -> LibfdxTarget.PSP
            iosCDeclared -> LibfdxTarget.IOS_C
            else -> null
        }
    }

    private fun wantsDesktopCTarget(extension: LibfdxExtension, requestedTasks: Set<String>): Boolean {
        return requestedTasks.any { taskName(it).startsWith("libfdx_desktop_c_") }
            || extension.desktopC.targets.any { target ->
                desktopCTargetTaskNames(target.name).any { task -> taskRequested(requestedTasks, task) }
            }
    }

    private fun wantsPspTarget(extension: LibfdxExtension, requestedTasks: Set<String>): Boolean {
        return requestedTasks.any { taskName(it).startsWith("libfdx_psp_") }
            || extension.psp.targets.any { target ->
                pspTargetTaskNames(target.name).any { task -> taskRequested(requestedTasks, task) }
            }
    }

    private fun wantsIosCTarget(extension: LibfdxExtension, requestedTasks: Set<String>): Boolean {
        return requestedTasks.any { taskName(it).startsWith("libfdx_ios_c_") }
            || extension.iosC.targets.any { target ->
                iosCTargetTaskNames(target.name).any { task -> taskRequested(requestedTasks, task) }
                    || iosCAliasTaskRequested(requestedTasks, target.name)
            }
    }

    private fun selectedPspTargets(
        extension: LibfdxExtension,
        requestedTasks: Set<String>
    ): List<LibfdxPspTargetExtension> {
        return extension.psp.targets.filter { target ->
            pspTargetTaskNames(target.name).any { task -> taskRequested(requestedTasks, task) }
        }
    }

    private fun selectedDesktopCTargets(
        extension: LibfdxExtension,
        requestedTasks: Set<String>
    ): List<LibfdxDesktopCTargetExtension> {
        return extension.desktopC.targets.filter { target ->
            desktopCTargetTaskNames(target.name).any { task -> taskRequested(requestedTasks, task) }
        }
    }

    private fun selectedIosCTargets(
        extension: LibfdxExtension,
        requestedTasks: Set<String>
    ): List<LibfdxIosCTargetExtension> {
        return extension.iosC.targets.filter { target ->
            iosCTargetTaskNames(target.name).any { task -> taskRequested(requestedTasks, task) }
                || iosCAliasTaskRequested(requestedTasks, target.name)
        }
    }

    private fun taskRequested(requestedTasks: Set<String>, expectedTaskName: String): Boolean {
        return requestedTasks.any { requested ->
            val name = taskName(requested)
            name == expectedTaskName
        }
    }

    private fun iosCAliasTaskRequested(requestedTasks: Set<String>, targetName: String): Boolean {
        val suffix = "ios_c_${safeTaskName(targetName)}_generate"
        return requestedTasks.any { requested -> taskName(requested).endsWith(suffix) }
    }

    private fun taskName(taskPath: String): String {
        return taskPath.substringAfterLast(':')
    }

    private fun selectedDesktopCTarget(
        extension: LibfdxExtension,
        requestedTasks: Set<String>
    ): LibfdxDesktopCTargetExtension? {
        val selected = selectedDesktopCTargets(extension, requestedTasks)
        if(selected.isNotEmpty()) {
            return selected.first()
        }
        return extension.desktopC.targets.firstOrNull()
    }

    private fun selectedPspTarget(
        extension: LibfdxExtension,
        requestedTasks: Set<String>
    ): LibfdxPspTargetExtension? {
        val selected = selectedPspTargets(extension, requestedTasks)
        if(selected.isNotEmpty()) {
            return selected.first()
        }
        return extension.psp.targets.firstOrNull()
    }

    private fun selectedIosCTarget(
        extension: LibfdxExtension,
        requestedTasks: Set<String>
    ): LibfdxIosCTargetExtension? {
        val selected = selectedIosCTargets(extension, requestedTasks)
        if(selected.isNotEmpty()) {
            return selected.first()
        }
        return extension.iosC.targets.firstOrNull()
    }

    private fun requestedTaskNames(project: Project): Set<String> {
        val projectPath = if(project.path == ":") "" else project.path
        return project.gradle.startParameter.taskNames.mapNotNull { taskPath ->
            if(!taskPath.contains(":")) {
                taskPath
            }
            else {
                taskPath.removePrefix(projectPath)
                    .takeIf { suffix -> suffix.startsWith(":") && !suffix.drop(1).contains(":") }
                    ?.drop(1)
            }
        }.toSet()
    }

    private fun desktopCTargetTaskNames(targetName: String): List<String> {
        val prefix = targetTaskBase("libfdx_desktop_c", targetName)
        return listOf(
            "${prefix}_generate_debug",
            "${prefix}_generate_release",
            "${prefix}_build_debug",
            "${prefix}_build_release",
            "${prefix}_run_debug",
            "${prefix}_run_release"
        )
    }

    private fun pspTargetTaskNames(targetName: String): List<String> {
        val prefix = targetTaskBase("libfdx_psp", targetName)
        return listOf(
            "${prefix}_generate",
            "${prefix}_build",
            "${prefix}_ppsspp_capture"
        )
    }

    private fun iosCTargetTaskNames(targetName: String): List<String> {
        val prefix = targetTaskBase("libfdx_ios_c", targetName)
        return listOf("${prefix}_generate")
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

    private fun configureDesktopC(project: Project, extension: LibfdxExtension, requestedTasks: Set<String>) {
        val desktopC = extension.desktopC
        selectedDesktopCTarget(extension, requestedTasks)?.let { target ->
            if(!target.mainClass.isPresent && !desktopC.mainClass.isPresent) {
                throw GradleException("desktopC target '${target.name}' must declare mainClass.")
            }
            if(target.mainClass.isPresent) {
                desktopC.mainClass.set(target.mainClass)
            }
            desktopC.targetFileName.set(target.targetFileName)
        }
        applyDesktopCConfig(extension.cConfig, desktopC)
        project.tasks.named(TeaVMPlugin.C_TASK_NAME, GenerateCTask::class.java).configure {
            targetFileName.set(desktopC.targetFileName)
            properties.put("libfdx.native.backend", "desktop_c")
        }
    }

    private fun configurePsp(project: Project, extension: LibfdxExtension, requestedTasks: Set<String>) {
        val psp = extension.psp
        selectedPspTarget(extension, requestedTasks)?.let { target ->
            if(!target.mainClass.isPresent) {
                throw GradleException("PSP target '${target.name}' must declare mainClass.")
            }
            psp.mainClass.set(target.mainClass)
            psp.targetFileName.set(target.targetFileName)
        }
        applyPspConfig(extension.cConfig, psp)
        project.tasks.named(TeaVMPlugin.C_TASK_NAME, GenerateCTask::class.java).configure {
            targetFileName.set(psp.targetFileName)
            properties.put("libfdx.native.backend", "psp")
        }
    }

    private fun configureIosC(project: Project, extension: LibfdxExtension, requestedTasks: Set<String>) {
        val iosC = extension.iosC
        selectedIosCTarget(extension, requestedTasks)?.let { target ->
            if(!target.mainClass.isPresent && !iosC.mainClass.isPresent) {
                throw GradleException("iosC target '${target.name}' must declare mainClass.")
            }
            if(target.mainClass.isPresent) {
                iosC.mainClass.set(target.mainClass)
            }
            if(target.bundleIdentifier.isPresent) {
                iosC.bundleIdentifier.set(target.bundleIdentifier)
            }
            if(target.graphicsApi.isPresent) {
                iosC.graphicsApi.set(target.graphicsApi)
            }
            iosC.targetFileName.set(target.targetFileName)
        }
        applyIosCConfig(extension.cConfig, iosC)
        project.tasks.named(TeaVMPlugin.C_TASK_NAME, GenerateCTask::class.java).configure {
            targetFileName.set(iosC.targetFileName)
            properties.put("libfdx.native.backend", "ios_c")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyDesktopCConfig(cConfig: TeaVMCConfiguration, desktopC: LibfdxDesktopCExtension) {
        cConfig.outputDir.set(desktopC.outputDir)
        (cConfig.mainClass as org.gradle.api.provider.Property<String>).set(desktopC.mainClass)
        (cConfig.relativePathInOutputDir as org.gradle.api.provider.Property<String>)
            .set(desktopC.relativePathInOutputDir)
        (cConfig.optimization as org.gradle.api.provider.Property<OptimizationLevel>).set(desktopC.optimization)
        (cConfig.debugInformation as org.gradle.api.provider.Property<Boolean>).set(desktopC.debugInformation)
        (cConfig.fastGlobalAnalysis as org.gradle.api.provider.Property<Boolean>).set(desktopC.fastGlobalAnalysis)
        (cConfig.outOfProcess as org.gradle.api.provider.Property<Boolean>).set(desktopC.outOfProcess)
        (cConfig.processMemory as org.gradle.api.provider.Property<Int>).set(desktopC.processMemory)
        (cConfig.minHeapSize as org.gradle.api.provider.Property<Int>).set(desktopC.minHeapSize)
        (cConfig.maxHeapSize as org.gradle.api.provider.Property<Int>).set(desktopC.maxHeapSize)
        (cConfig.heapDump as org.gradle.api.provider.Property<Boolean>).set(desktopC.heapDump)
        (cConfig.shortFileNames as org.gradle.api.provider.Property<Boolean>).set(desktopC.shortFileNames)
        (cConfig.obfuscated as org.gradle.api.provider.Property<Boolean>).set(desktopC.obfuscated)
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyPspConfig(cConfig: TeaVMCConfiguration, psp: LibfdxPspExtension) {
        cConfig.outputDir.set(psp.outputDir)
        (cConfig.mainClass as org.gradle.api.provider.Property<String>).set(psp.mainClass)
        (cConfig.relativePathInOutputDir as org.gradle.api.provider.Property<String>).set(psp.relativePathInOutputDir)
        (cConfig.optimization as org.gradle.api.provider.Property<OptimizationLevel>).set(psp.optimization)
        (cConfig.debugInformation as org.gradle.api.provider.Property<Boolean>).set(psp.debugInformation)
        (cConfig.fastGlobalAnalysis as org.gradle.api.provider.Property<Boolean>).set(psp.fastGlobalAnalysis)
        (cConfig.outOfProcess as org.gradle.api.provider.Property<Boolean>).set(psp.outOfProcess)
        (cConfig.processMemory as org.gradle.api.provider.Property<Int>).set(psp.processMemory)
        (cConfig.minHeapSize as org.gradle.api.provider.Property<Int>).set(psp.minHeapSize)
        (cConfig.maxHeapSize as org.gradle.api.provider.Property<Int>).set(psp.maxHeapSize)
        (cConfig.heapDump as org.gradle.api.provider.Property<Boolean>).set(psp.heapDump)
        (cConfig.shortFileNames as org.gradle.api.provider.Property<Boolean>).set(psp.shortFileNames)
        (cConfig.obfuscated as org.gradle.api.provider.Property<Boolean>).set(psp.obfuscated)
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyIosCConfig(cConfig: TeaVMCConfiguration, iosC: LibfdxIosCExtension) {
        cConfig.outputDir.set(iosC.outputDir)
        (cConfig.mainClass as org.gradle.api.provider.Property<String>).set(iosC.mainClass)
        (cConfig.relativePathInOutputDir as org.gradle.api.provider.Property<String>).set(iosC.relativePathInOutputDir)
        (cConfig.optimization as org.gradle.api.provider.Property<OptimizationLevel>).set(iosC.optimization)
        (cConfig.debugInformation as org.gradle.api.provider.Property<Boolean>).set(iosC.debugInformation)
        (cConfig.fastGlobalAnalysis as org.gradle.api.provider.Property<Boolean>).set(iosC.fastGlobalAnalysis)
        (cConfig.outOfProcess as org.gradle.api.provider.Property<Boolean>).set(iosC.outOfProcess)
        (cConfig.processMemory as org.gradle.api.provider.Property<Int>).set(iosC.processMemory)
        (cConfig.minHeapSize as org.gradle.api.provider.Property<Int>).set(iosC.minHeapSize)
        (cConfig.maxHeapSize as org.gradle.api.provider.Property<Int>).set(iosC.maxHeapSize)
        (cConfig.heapDump as org.gradle.api.provider.Property<Boolean>).set(iosC.heapDump)
        (cConfig.shortFileNames as org.gradle.api.provider.Property<Boolean>).set(iosC.shortFileNames)
        (cConfig.obfuscated as org.gradle.api.provider.Property<Boolean>).set(iosC.obfuscated)
    }

    private fun registerTasks(project: Project, extension: LibfdxExtension) {
        if(extension.ecsProject.enabled.get()) {
            registerEcsProjectBundleTask(project, extension.ecsProject)
        }
        if(extension.isDeclared(LibfdxTarget.JS)) {
            registerJsTasks(project, extension)
        }
        if(extension.isDeclared(LibfdxTarget.WASM)) {
            registerWasmTasks(project, extension)
        }
        if(extension.isDeclared(LibfdxTarget.DESKTOP_JVM)) {
            registerDesktopJvmTasks(project, extension)
        }
        if(extension.isDeclared(LibfdxTarget.DESKTOP_C)) {
            registerDesktopCTargetTasks(project, extension)
            registerDesktopCTasks(project, extension)
        }
        if(extension.isDeclared(LibfdxTarget.PSP)) {
            registerPspTargetTasks(project, extension)
            registerPspTasks(project, extension)
        }
        if(extension.isDeclared(LibfdxTarget.IOS_C)) {
            registerIosCTargetTasks(project, extension)
            registerIosCTasks(project, extension)
        }
    }

    private fun registerEcsProjectBundleTask(project: Project, extension: LibfdxEcsProjectExtension) {
        project.tasks.register<LibfdxEcsProjectBundleTask>(ECS_PROJECT_BUNDLE_TASK) {
            group = TASK_GROUP
            description = "Builds the portable ECS project bundle consumed by the desktop editor."
            dependsOn(JavaPlugin.CLASSES_TASK_NAME)
            projectId.set(extension.projectId)
            entryClass.set(extension.entryClass)
            projectManifest.set(extension.projectManifest)
            assetsDirectory.set(extension.assetsDirectory)
            scenesDirectory.set(extension.scenesDirectory)
            projectClasses.from(extension.projectClasses)
            allowedDependencies.from(extension.allowedDependencies)
            toolingAbi.set(extension.toolingAbi)
            libfdxAbi.set(extension.libfdxAbi)
            gradleRoot.set(extension.gradleRoot)
            gradleProject.set(extension.gradleProject)
            desktopBundleTask.set(extension.desktopBundleTask)
            outputFile.set(extension.outputFile)
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
            description = "Generate configured libfdx bitmap fonts."
            dependsOn(fontTasks)
        }
    }

    private fun registerShaderTasks(project: Project, extension: LibfdxExtension) {
        val validate = project.tasks.register<LibfdxValidateShadersTask>("libfdx_validate_shaders") {
            group = TASK_GROUP
            description = "Validate libfdx WGSL shader profiles under src/main/fdx-shaders."
            sourceDir.set(extension.shaders.sourceDir)
            defaultProfile.set(extension.shaders.defaultProfile)
            reportFile.set(extension.shaders.reportFile)
        }
        project.tasks.named("check").configure {
            dependsOn(validate)
        }
    }

    private fun registerJsTasks(project: Project, extension: LibfdxExtension) {
        val runtimeClasspath = project.extensions.getByType<SourceSetContainer>().getByName("main").runtimeClasspath
        val runtimeFdxWeb = project.takeIf { usesLocalLibfdxRuntime(it) }
            ?.rootProject
            ?.findProject(":libfdx:framework:fdx:platform:web")
        val runtimeFdxWebResources = runtimeFdxWeb?.tasks?.matching { it.name == "generate_runtime_fdx_web_native" }
        val runtimeFdxWebResourcesDir = runtimeFdxWeb?.layout?.buildDirectory?.dir("generated/resources/runtimeFdxWeb")
        val hasTargets = extension.js.targets.isNotEmpty()
        val prepare = project.tasks.register<LibfdxWebAppTask>(
            internalTaskName("libfdx_web_js_prepare", hasTargets)
        ) {
            group = if(hasTargets) null else TASK_GROUP
            description = "Generate the libfdx Web JavaScript web application shell."
            dependsOn(project.tasks.named(TeaVMPlugin.JS_TASK_NAME))
            runtimeFdxWebResources?.let { dependsOn(it) }
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
            runtimeFdxWebResourcesDir?.let { this.runtimeClasspath.from(it) }
        }
        if(!hasTargets) {
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
        registerWebTargets(project, extension.js, "libfdx_web_js", prepare)
    }

    private fun registerWasmTasks(project: Project, extension: LibfdxExtension) {
        val runtimeClasspath = project.extensions.getByType<SourceSetContainer>().getByName("main").runtimeClasspath
        val runtimeFdxWeb = project.takeIf { usesLocalLibfdxRuntime(it) }
            ?.rootProject
            ?.findProject(":libfdx:framework:fdx:platform:web")
        val runtimeFdxWebResources = runtimeFdxWeb?.tasks?.matching { it.name == "generate_runtime_fdx_web_native" }
        val runtimeFdxWebResourcesDir = runtimeFdxWeb?.layout?.buildDirectory?.dir("generated/resources/runtimeFdxWeb")
        val hasTargets = extension.wasm.targets.isNotEmpty()
        val prepare = project.tasks.register<LibfdxWebAppTask>(
            internalTaskName("libfdx_web_wasm_prepare", hasTargets)
        ) {
            group = if(hasTargets) null else TASK_GROUP
            description = "Generate the libfdx Web Wasm web application shell."
            dependsOn(project.tasks.named(TeaVMPlugin.BUILD_WASM_GC_TASK_NAME))
            runtimeFdxWebResources?.let { dependsOn(it) }
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
            runtimeFdxWebResourcesDir?.let { this.runtimeClasspath.from(it) }
        }
        if(!hasTargets) {
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
        registerWebTargets(project, extension.wasm, "libfdx_web_wasm", prepare)
    }

    private fun registerWebTargets(
        project: Project,
        web: LibfdxWebExtension,
        taskBaseName: String,
        prepare: TaskProvider<*>
    ) {
        web.targets.forEach { target ->
            val targetTaskBaseName = targetTaskBase(taskBaseName, target.name)
            val build = project.tasks.register("${targetTaskBaseName}_build") {
                group = TASK_GROUP
                description = target.buildDescription.orElse(
                    "Builds the ${target.name} libfdx web application."
                ).get()
                dependsOn(prepare)
            }
            project.tasks.register<LibfdxRunWebTask>("${targetTaskBaseName}_run") {
                group = TASK_GROUP
                description = target.runDescription.orElse(
                    "Builds and serves the ${target.name} libfdx web application."
                ).get()
                dependsOn(build)
                webappDir.set(web.webappDir())
                port.set(web.serverPort)
                defaultPath.set(target.defaultPath)
            }
        }
    }

    private fun usesLocalLibfdxRuntime(project: Project): Boolean {
        return hasLocalLibfdxRuntimeDependency(project, mutableSetOf())
    }

    private fun hasLocalLibfdxRuntimeDependency(project: Project, visited: MutableSet<String>): Boolean {
        if(!visited.add(project.path)) {
            return false
        }
        val runtimeClasspath = project.configurations.findByName("runtimeClasspath") ?: return false
        return runtimeClasspath.allDependencies
            .withType(ProjectDependency::class.java)
            .any {
                val dependencyProject = project.rootProject.findProject(it.path)
                it.path.startsWith(":libfdx:") ||
                    (dependencyProject != null && hasLocalLibfdxRuntimeDependency(dependencyProject, visited))
            }
    }

    private fun registerDesktopJvmTasks(project: Project, extension: LibfdxExtension) {
        val desktopJvm = extension.desktopJvm
        val sourceSets = project.extensions.getByType<SourceSetContainer>()
        val mainRuntimeClasspath = sourceSets.getByName("main").runtimeClasspath
        val applicationRuntimeClasspath = if(desktopJvm.runtimeClasspath.isEmpty) {
            mainRuntimeClasspath
        }
        else {
            desktopJvm.runtimeClasspath
        }
        val toolchains = project.extensions.getByType<JavaToolchainService>()
        val configuredTargets: List<LibfdxDesktopJvmTargetExtension?> = if(desktopJvm.targets.isEmpty()) {
            listOf(null)
        }
        else {
            desktopJvm.targets.toList()
        }
        configuredTargets.forEach { target ->
            val taskBaseName = target?.let { targetTaskBase("libfdx_desktop_jvm", it.name) }
                ?: "libfdx_desktop_jvm"
            val releaseClasspath = prioritizedDesktopJvmClasspath(
                target?.runtimeClasspath ?: project.files(),
                applicationRuntimeClasspath
            )
            val launchProperties = desktopJvm.launchProperties.get() + (target?.launchProperties?.get() ?: emptyMap())
            val displayName = target?.displayName?.get() ?: "libfdx desktop JVM"
            val launchDefaults = project.layout.buildDirectory.file(
                "generated/desktop-jvm/$taskBaseName/${desktopJvm.launchPropertiesResourceName.get()}"
            )
            val writeLaunchDefaults = if(launchProperties.isNotEmpty()) {
                project.tasks.register("${taskBaseName}_write_launch_defaults") {
                    outputs.file(launchDefaults)
                    doLast {
                        val output = launchDefaults.get().asFile
                        output.parentFile.mkdirs()
                        output.writeText(
                            launchProperties.entries.joinToString(System.lineSeparator()) { (name, value) ->
                                "$name=$value"
                            } + System.lineSeparator(),
                            Charsets.UTF_8
                        )
                    }
                }
            }
            else {
                null
            }
            val buildTask = project.tasks.register<Jar>("${taskBaseName}_build") {
                group = TASK_GROUP
                description = target?.buildDescription?.orElse(
                    "Builds the $displayName desktop JVM release jar."
                )?.get() ?: "Builds the $displayName desktop JVM release jar."
                dependsOn("classes", releaseClasspath)
                writeLaunchDefaults?.let { dependsOn(it) }
                inputs.property("libfdxDesktopJvmClasspathPriority", "target-first")
                archiveFileName.set("$taskBaseName.jar")
                destinationDirectory.set(desktopJvm.outputDir)
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                isZip64 = true
                manifest {
                    val manifestAttributes = linkedMapOf<String, String>(
                        "Main-Class" to desktopJvm.mainClass.get(),
                        "Multi-Release" to "true"
                    )
                    if(desktopJvm.enableNativeAccess.get()) {
                        manifestAttributes["Enable-Native-Access"] = "ALL-UNNAMED"
                    }
                    attributes(manifestAttributes)
                }
                exclude("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF")
                from({
                    releaseClasspath.files
                        .filter { it.exists() }
                        .map { if(it.isDirectory) it else project.zipTree(it) }
                })
                writeLaunchDefaults?.let {
                    from(launchDefaults.map { file -> file.asFile }) {
                        rename { desktopJvm.launchPropertiesResourceName.get() }
                    }
                }
            }
            project.tasks.register<JavaExec>("${taskBaseName}_run") {
                group = TASK_GROUP
                description = target?.runDescription?.orElse(
                    "Runs the $displayName desktop JVM application."
                )?.get() ?: "Runs the $displayName desktop JVM application."
                dependsOn(buildTask)
                classpath = releaseClasspath
                mainClass.set(desktopJvm.mainClass)
                workingDir = desktopJvm.workingDir.get().asFile
                javaLauncher.set(toolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(desktopJvm.javaLanguageVersion.get()))
                })
                jvmArgs(desktopJvm.jvmArgs.get())
                if(desktopJvm.enableNativeAccess.get()
                    && desktopJvm.jvmArgs.get().none { it.startsWith("--enable-native-access") }) {
                    jvmArgs("--enable-native-access=ALL-UNNAMED")
                }
                configureDesktopJvmSystemProperties(project, this, desktopJvm, target)
            }
        }
    }

    private fun configureDesktopJvmSystemProperties(
        project: Project,
        task: JavaExec,
        desktopJvm: LibfdxDesktopJvmExtension,
        target: LibfdxDesktopJvmTargetExtension?
    ) {
        val defaults = desktopJvm.defaultSystemProperties.get() + (target?.defaultSystemProperties?.get() ?: emptyMap())
        defaults.forEach { (name, fallback) ->
            task.systemProperty(name, configuredSystemProperty(project, name) ?: fallback)
        }
        val forwardedNames = desktopJvm.forwardedSystemProperties.get() + (target?.forwardedSystemProperties?.get()
            ?: emptySet())
        forwardedNames.forEach { name ->
            configuredSystemProperty(project, name)?.takeIf { it.isNotBlank() }?.let { value ->
                task.systemProperty(name, value)
            }
        }
        val prefixes = desktopJvm.forwardedSystemPropertyPrefixes.get() + (target?.forwardedSystemPropertyPrefixes?.get()
            ?: emptySet())
        if(prefixes.isNotEmpty()) {
            project.gradle.startParameter.systemPropertiesArgs
                .filterKeys { name -> prefixes.any { prefix -> name.startsWith(prefix) } }
                .filterValues { value -> value.isNotBlank() }
                .forEach { (name, value) -> task.systemProperty(name, value) }
        }
        val fixedProperties = desktopJvm.systemProperties.get() + (target?.systemProperties?.get() ?: emptyMap())
        fixedProperties.forEach { (name, value) ->
            task.systemProperty(name, value)
        }
    }

    private fun configuredSystemProperty(project: Project, name: String): String? {
        return project.gradle.startParameter.systemPropertiesArgs[name] ?: System.getProperty(name)
    }

    private fun registerDesktopCTasks(project: Project, extension: LibfdxExtension) {
        val sourceSets = project.extensions.getByType<SourceSetContainer>()
        val runtimeClasspath = sourceSets.getByName("main").runtimeClasspath
        val hasTargets = extension.desktopC.targets.isNotEmpty()
        val generate = project.tasks.register<LibfdxDesktopCProjectTask>(
            internalTaskName("libfdx_desktop_c_generate", hasTargets)
        ) {
            group = if(hasTargets) null else TASK_GROUP
            description = "Generate the libfdx desktop_c project."
            dependsOn(project.tasks.named(TeaVMPlugin.C_TASK_NAME))
            buildRoot.set(extension.desktopC.outputDir)
            generatedSourcesDir.set(extension.desktopC.generatedSourcesDir())
            releaseDir.set(extension.desktopC.releasePath)
            projectName.set(extension.desktopC.targetFileName)
            buildType.set(extension.desktopC.buildType)
            showConsole.set(extension.desktopC.showConsole)
            nativeResourceClasspath.from(runtimeClasspath)
        }
        val buildDebug = project.tasks.register<LibfdxNativeBuildTask>(
            internalTaskName("libfdx_desktop_c_build_debug", hasTargets)
        ) {
            group = if(hasTargets) null else TASK_GROUP
            description = "Generate and build the libfdx desktop_c Debug executable."
            dependsOn(generate)
            buildRoot.set(extension.desktopC.outputDir)
            scriptBaseName.set("app_debug")
        }
        val buildRelease = project.tasks.register<LibfdxNativeBuildTask>(
            internalTaskName("libfdx_desktop_c_build_release", hasTargets)
        ) {
            group = if(hasTargets) null else TASK_GROUP
            description = "Generate and build the libfdx desktop_c Release executable."
            dependsOn(generate)
            buildRoot.set(extension.desktopC.outputDir)
            scriptBaseName.set("app_release")
        }
        project.tasks.register<LibfdxDesktopCRunTask>(
            internalTaskName("libfdx_desktop_c_run_debug", hasTargets)
        ) {
            group = if(hasTargets) null else TASK_GROUP
            description = "Generate, build, and run the libfdx desktop_c Debug executable."
            dependsOn(buildDebug)
            releaseDir.set(extension.desktopC.releasePath)
            projectName.set(extension.desktopC.targetFileName)
            buildType.set("Debug")
            openConsole.set(extension.desktopC.openConsole)
            runArgs.set(project.providers.gradleProperty("libfdx.desktopC.runArgs").map { value ->
                parseCommandLineArguments(value)
            }.orElse(emptyList()))
        }
        project.tasks.register<LibfdxDesktopCRunTask>(
            internalTaskName("libfdx_desktop_c_run_release", hasTargets)
        ) {
            group = if(hasTargets) null else TASK_GROUP
            description = "Generate, build, and run the libfdx desktop_c Release executable."
            dependsOn(buildRelease)
            releaseDir.set(extension.desktopC.releasePath)
            projectName.set(extension.desktopC.targetFileName)
            buildType.set("Release")
            openConsole.set(extension.desktopC.openConsole)
            runArgs.set(project.providers.gradleProperty("libfdx.desktopC.runArgs").map { value ->
                parseCommandLineArguments(value)
            }.orElse(emptyList()))
        }
    }

    private fun registerDesktopCTargetTasks(project: Project, extension: LibfdxExtension) {
        extension.desktopC.targets.forEach { target ->
            val label = target.displayName.get()
            val taskBaseName = targetTaskBase("libfdx_desktop_c", target.name)
            val generate = internalTaskName("libfdx_desktop_c_generate", true)
            val buildDebug = internalTaskName("libfdx_desktop_c_build_debug", true)
            val buildRelease = internalTaskName("libfdx_desktop_c_build_release", true)
            val runDebug = internalTaskName("libfdx_desktop_c_run_debug", true)
            val runRelease = internalTaskName("libfdx_desktop_c_run_release", true)
            project.tasks.register("${taskBaseName}_generate_debug") {
                group = TASK_GROUP
                description = "Generates the $label Debug project."
                dependsOn(generate)
            }
            project.tasks.register("${taskBaseName}_generate_release") {
                group = TASK_GROUP
                description = "Generates the $label Release project."
                dependsOn(generate)
            }
            project.tasks.register("${taskBaseName}_build_debug") {
                group = TASK_GROUP
                description = "Builds the $label Debug executable."
                dependsOn(buildDebug)
            }
            project.tasks.register("${taskBaseName}_build_release") {
                group = TASK_GROUP
                description = "Builds the $label Release executable."
                dependsOn(buildRelease)
            }
            project.tasks.register("${taskBaseName}_run_debug") {
                group = TASK_GROUP
                description = "Runs the $label Debug executable."
                dependsOn(runDebug)
            }
            project.tasks.register("${taskBaseName}_run_release") {
                group = TASK_GROUP
                description = "Runs the $label Release executable."
                dependsOn(runRelease)
            }
        }
    }

    private fun registerPspTasks(project: Project, extension: LibfdxExtension) {
        val sourceSets = project.extensions.getByType<SourceSetContainer>()
        val runtimeClasspath = sourceSets.getByName("main").runtimeClasspath
        val hasTargets = extension.psp.targets.isNotEmpty()
        val generate = project.tasks.register<LibfdxPspProjectTask>(
            internalTaskName("libfdx_psp_generate", hasTargets)
        ) {
            group = if(hasTargets) null else TASK_GROUP
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
        val build = project.tasks.register<LibfdxNativeBuildTask>(internalTaskName("libfdx_psp_build", hasTargets)) {
            group = if(hasTargets) null else TASK_GROUP
            description = "Generate and build the libfdx PSP EBOOT project."
            dependsOn(generate)
            buildRoot.set(extension.psp.outputDir)
            scriptBaseName.set("build")
        }
        project.tasks.register<LibfdxPspPpssppCaptureTask>(
            internalTaskName("libfdx_psp_ppsspp_capture", hasTargets)
        ) {
            group = if(hasTargets) null else TASK_GROUP
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

    private fun registerIosCTasks(project: Project, extension: LibfdxExtension) {
        val sourceSets = project.extensions.getByType<SourceSetContainer>()
        val runtimeClasspath = sourceSets.getByName("main").runtimeClasspath
        val hasTargets = extension.iosC.targets.isNotEmpty()
        project.tasks.register<LibfdxIosCProjectTask>(
            internalTaskName("libfdx_ios_c_generate", hasTargets)
        ) {
            group = if(hasTargets) null else TASK_GROUP
            description = "Generate the libfdx iOS C TeaVM and Xcode project."
            dependsOn(project.tasks.named(TeaVMPlugin.C_TASK_NAME))
            buildRoot.set(extension.iosC.outputDir)
            generatedSourcesDir.set(extension.iosC.generatedSourcesDir())
            releaseDir.set(extension.iosC.releasePath)
            xcodeProjectDir.set(extension.iosC.xcodeProjectDir)
            projectName.set(extension.iosC.targetFileName)
            bundleIdentifier.set(extension.iosC.bundleIdentifier)
            graphicsApi.set(extension.iosC.graphicsApi)
            nativeResourceClasspath.from(runtimeClasspath)
            assets.from(extension.assets)
        }
    }

    private fun registerIosCTargetTasks(project: Project, extension: LibfdxExtension) {
        extension.iosC.targets.forEach { target ->
            val label = target.displayName.get()
            val taskBaseName = targetTaskBase("libfdx_ios_c", target.name)
            val generate = internalTaskName("libfdx_ios_c_generate", true)
            project.tasks.register("${taskBaseName}_generate") {
                group = TASK_GROUP
                description = "Generates the $label iOS C TeaVM and Xcode project."
                dependsOn(generate)
            }
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

    private fun registerPspTargetTasks(project: Project, extension: LibfdxExtension) {
        extension.psp.targets.forEach { target ->
            val label = target.displayName.get()
            val taskBaseName = targetTaskBase("libfdx_psp", target.name)
            val generate = internalTaskName("libfdx_psp_generate", true)
            val build = internalTaskName("libfdx_psp_build", true)
            val capture = internalTaskName("libfdx_psp_ppsspp_capture", true)
            project.tasks.register("${taskBaseName}_generate") {
                group = TASK_GROUP
                description = "Generates the $label PSP TeaVM C project."
                dependsOn(generate)
            }
            project.tasks.register("${taskBaseName}_build") {
                group = TASK_GROUP
                description = "Generates and builds the $label PSP EBOOT project."
                dependsOn(build)
            }
            project.tasks.register("${taskBaseName}_ppsspp_capture") {
                group = TASK_GROUP
                description = "Builds the $label PSP EBOOT and captures a PPSSPP emulator frame."
                dependsOn(capture)
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

    private fun targetTaskBase(taskBaseName: String, targetName: String): String {
        return "${taskBaseName}_${safeTaskName(targetName)}"
    }

    private fun internalTaskName(taskName: String, useInternalName: Boolean): String {
        return if(useInternalName) "${taskName}_internal" else taskName
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
