package io.github.libfdx.gradle

import io.github.libfdx.tools.font.BitmapFontSpec
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.teavm.gradle.api.OptimizationLevel
import org.teavm.gradle.api.SourceFilePolicy
import org.teavm.gradle.api.TeaVMConfiguration
import org.teavm.gradle.api.TeaVMCConfiguration
import org.teavm.gradle.api.TeaVMJSConfiguration
import org.teavm.gradle.api.TeaVMWasmGCConfiguration
import javax.inject.Inject

open class LibfdxExtension @Inject constructor(
    private val objects: ObjectFactory,
    private val project: Project,
    private val jsConfig: TeaVMJSConfiguration,
    private val wasmConfig: TeaVMWasmGCConfiguration,
    internal val cConfig: TeaVMCConfiguration
) {
    internal val declaredTargets = linkedSetOf<LibfdxTarget>()

    val assets: ConfigurableFileCollection = project.files()
    val bitmapFonts: NamedDomainObjectContainer<LibfdxBitmapFontExtension> =
        objects.domainObjectContainer(LibfdxBitmapFontExtension::class.java) { name ->
            objects.newInstance(LibfdxBitmapFontExtension::class.java, name)
        }
    val shaders: LibfdxShadersExtension =
        objects.newInstance(LibfdxShadersExtension::class.java, project, objects)

    val js: LibfdxJsExtension by lazy {
        objects.newInstance(LibfdxJsExtension::class.java, project, jsConfig)
    }

    val wasm: LibfdxWasmExtension by lazy {
        objects.newInstance(LibfdxWasmExtension::class.java, project, wasmConfig)
    }

    val desktopC: LibfdxDesktopCExtension by lazy {
        objects.newInstance(LibfdxDesktopCExtension::class.java, project, objects)
    }

    val desktopJvm: LibfdxDesktopJvmExtension by lazy {
        objects.newInstance(LibfdxDesktopJvmExtension::class.java, project, objects)
    }

    val psp: LibfdxPspExtension by lazy {
        objects.newInstance(LibfdxPspExtension::class.java, project, objects)
    }

    val iosC: LibfdxIosCExtension by lazy {
        objects.newInstance(LibfdxIosCExtension::class.java, project, objects)
    }

    fun assets(vararg paths: Any) {
        assets.from(*paths)
    }

    fun bitmapFont(name: String, action: Action<in LibfdxBitmapFontExtension>) {
        bitmapFonts.create(name, action)
    }

    fun bitmapFonts(action: Action<in NamedDomainObjectContainer<LibfdxBitmapFontExtension>>) {
        action.execute(bitmapFonts)
    }

    fun shaders(action: Action<in LibfdxShadersExtension>) {
        action.execute(shaders)
    }

    fun js(action: Action<in LibfdxJsExtension>) {
        declaredTargets.add(LibfdxTarget.JS)
        action.execute(js)
    }

    fun wasm(action: Action<in LibfdxWasmExtension>) {
        declaredTargets.add(LibfdxTarget.WASM)
        action.execute(wasm)
    }

    fun desktopC(action: Action<in LibfdxDesktopCExtension>) {
        declaredTargets.add(LibfdxTarget.DESKTOP_C)
        action.execute(desktopC)
    }

    fun desktopJvm(action: Action<in LibfdxDesktopJvmExtension>) {
        declaredTargets.add(LibfdxTarget.DESKTOP_JVM)
        action.execute(desktopJvm)
    }

    fun psp(action: Action<in LibfdxPspExtension>) {
        declaredTargets.add(LibfdxTarget.PSP)
        action.execute(psp)
    }

    fun iosC(action: Action<in LibfdxIosCExtension>) {
        declaredTargets.add(LibfdxTarget.IOS_C)
        action.execute(iosC)
    }

    internal fun isDeclared(target: LibfdxTarget): Boolean {
        return declaredTargets.contains(target)
    }
}

open class LibfdxDesktopJvmExtension @Inject constructor(
    project: Project,
    objects: ObjectFactory
) {
    val mainClass: Property<String> = objects.property(String::class.java)
    val outputDir: DirectoryProperty = objects.directoryProperty()
        .convention(project.layout.buildDirectory.dir("dist/desktop-jvm"))
    val workingDir: DirectoryProperty = objects.directoryProperty()
        .convention(project.rootProject.layout.projectDirectory)
    val javaLanguageVersion: Property<Int> = objects.property(Int::class.javaObjectType).convention(25)
    val enableNativeAccess: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val runtimeClasspath: ConfigurableFileCollection = project.files()
    val launchPropertiesResourceName: Property<String> = objects.property(String::class.java)
        .convention("libfdx-desktop-launch.properties")
    val jvmArgs: ListProperty<String> = objects.listProperty(String::class.java)
        .convention(listOf("-Dorg.lwjgl.system.stackSize=1048576"))
    val systemProperties: MapProperty<String, String> = objects.mapProperty(String::class.java, String::class.java)
        .convention(emptyMap())
    val defaultSystemProperties: MapProperty<String, String> =
        objects.mapProperty(String::class.java, String::class.java).convention(emptyMap())
    val launchProperties: MapProperty<String, String> = objects.mapProperty(String::class.java, String::class.java)
        .convention(emptyMap())
    val forwardedSystemProperties: SetProperty<String> = objects.setProperty(String::class.java)
        .convention(emptySet())
    val forwardedSystemPropertyPrefixes: SetProperty<String> = objects.setProperty(String::class.java)
        .convention(emptySet())
    val targets: NamedDomainObjectContainer<LibfdxDesktopJvmTargetExtension> =
        objects.domainObjectContainer(LibfdxDesktopJvmTargetExtension::class.java) { name ->
            objects.newInstance(LibfdxDesktopJvmTargetExtension::class.java, name, project, objects)
        }

    fun target(name: String, action: Action<in LibfdxDesktopJvmTargetExtension>) {
        targets.create(name, action)
    }

    fun jvmArg(value: String) {
        jvmArgs.add(value)
    }

    fun runtimeClasspath(vararg paths: Any) {
        runtimeClasspath.from(*paths)
    }

    fun systemProperty(name: String, value: String) {
        systemProperties.put(name, value)
    }

    fun defaultSystemProperty(name: String, value: String) {
        defaultSystemProperties.put(name, value)
    }

    fun launchProperty(name: String, value: String) {
        launchProperties.put(name, value)
    }

    fun forwardSystemProperty(name: String) {
        forwardedSystemProperties.add(name)
    }

    fun forwardSystemPropertyPrefix(prefix: String) {
        forwardedSystemPropertyPrefixes.add(prefix)
    }
}

open class LibfdxDesktopJvmTargetExtension @Inject constructor(
    private val targetName: String,
    project: Project,
    objects: ObjectFactory
) : Named {
    val displayName: Property<String> = objects.property(String::class.java).convention(targetName)
    val runtimeClasspath: ConfigurableFileCollection = project.files()
    val buildDescription: Property<String> = objects.property(String::class.java)
    val runDescription: Property<String> = objects.property(String::class.java)
    val systemProperties: MapProperty<String, String> = objects.mapProperty(String::class.java, String::class.java)
        .convention(emptyMap())
    val defaultSystemProperties: MapProperty<String, String> =
        objects.mapProperty(String::class.java, String::class.java).convention(emptyMap())
    val launchProperties: MapProperty<String, String> = objects.mapProperty(String::class.java, String::class.java)
        .convention(emptyMap())
    val forwardedSystemProperties: SetProperty<String> = objects.setProperty(String::class.java)
        .convention(emptySet())
    val forwardedSystemPropertyPrefixes: SetProperty<String> = objects.setProperty(String::class.java)
        .convention(emptySet())

    override fun getName(): String {
        return targetName
    }

    fun runtimeClasspath(vararg paths: Any) {
        runtimeClasspath.from(*paths)
    }

    fun systemProperty(name: String, value: String) {
        systemProperties.put(name, value)
    }

    fun defaultSystemProperty(name: String, value: String) {
        defaultSystemProperties.put(name, value)
    }

    fun launchProperty(name: String, value: String) {
        launchProperties.put(name, value)
    }

    fun forwardSystemProperty(name: String) {
        forwardedSystemProperties.add(name)
    }

    fun forwardSystemPropertyPrefix(prefix: String) {
        forwardedSystemPropertyPrefixes.add(prefix)
    }
}

open class LibfdxShadersExtension @Inject constructor(
    project: Project,
    objects: ObjectFactory
) {
    val sourceDir: DirectoryProperty = objects.directoryProperty()
        .convention(project.layout.projectDirectory.dir("src/main/fdx-shaders"))
    val defaultProfile: Property<String> = objects.property(String::class.java)
        .convention("webgpu")
    val reportFile: RegularFileProperty = objects.fileProperty()
        .convention(project.layout.buildDirectory.file("reports/libfdx/shaders/validation.md"))
}

open class LibfdxBitmapFontExtension @Inject constructor(
    private val fontName: String,
    objects: ObjectFactory
) : Named {
    val sourceFile: RegularFileProperty = objects.fileProperty()
    val outputDir: DirectoryProperty = objects.directoryProperty()
    val assetPath: Property<String> = objects.property(String::class.java)
        .convention(BitmapFontSpec.DEFAULT_ASSET_PATH)
    val size: Property<Int> = objects.property(Int::class.javaObjectType).convention(24)
    val padding: Property<Int> = objects.property(Int::class.javaObjectType).convention(2)
    val maxTextureSize: Property<Int> = objects.property(Int::class.javaObjectType).convention(512)
    val characters: Property<String> = objects.property(String::class.java)
        .convention(defaultCharacters())

    override fun getName(): String {
        return fontName
    }

    private fun defaultCharacters(): String {
        val builder = StringBuilder()
        for(code in 32..126) {
            builder.append(code.toChar())
        }
        return builder.toString()
    }
}

@Suppress("UNCHECKED_CAST")
open class LibfdxTargetExtension internal constructor(
    internal val teavmConfig: TeaVMConfiguration
) {
    val outputDir: DirectoryProperty
        get() = teavmConfig.outputDir

    val mainClass: Property<String>
        get() = teavmConfig.mainClass as Property<String>

    val relativePathInOutputDir: Property<String>
        get() = teavmConfig.relativePathInOutputDir as Property<String>

    val optimization: Property<OptimizationLevel>
        get() = teavmConfig.optimization as Property<OptimizationLevel>

    val debugInformation: Property<Boolean>
        get() = teavmConfig.debugInformation as Property<Boolean>

    val fastGlobalAnalysis: Property<Boolean>
        get() = teavmConfig.fastGlobalAnalysis as Property<Boolean>

    val outOfProcess: Property<Boolean>
        get() = teavmConfig.outOfProcess as Property<Boolean>

    val processMemory: Property<Int>
        get() = teavmConfig.processMemory as Property<Int>

    internal fun outputSubDir(): Provider<Directory> {
        return outputDir.flatMap { output ->
            relativePathInOutputDir.map { relativePath ->
                output.dir(relativePath)
            }
        }
    }
}

open class LibfdxWebExtension @Inject constructor(
    private val objects: ObjectFactory,
    project: Project,
    teavmConfig: TeaVMConfiguration
) : LibfdxTargetExtension(teavmConfig) {
    open val entryPointName: Property<String> = objects.property(String::class.java).convention("main")
    val mainClassArgs: Property<String> = objects.property(String::class.java).convention("")
    val htmlTitle: Property<String> = objects.property(String::class.java).convention("libfdx")
    val htmlWidth: Property<Int> = objects.property(Int::class.javaObjectType).convention(640)
    val htmlHeight: Property<Int> = objects.property(Int::class.javaObjectType).convention(480)
    val canvasId: Property<String> = objects.property(String::class.java).convention("libfdx-canvas")
    val serverPort: Property<Int> = objects.property(Int::class.javaObjectType)
        .convention(project.providers.gradleProperty("libfdx.web.port").map(String::toInt).orElse(8080))
    val targets: NamedDomainObjectContainer<LibfdxWebTargetExtension> =
        objects.domainObjectContainer(LibfdxWebTargetExtension::class.java) { name ->
            objects.newInstance(LibfdxWebTargetExtension::class.java, name, objects)
        }

    fun target(name: String, action: Action<in LibfdxWebTargetExtension>) {
        targets.create(name, action)
    }

    internal fun webappDir(): Provider<Directory> {
        return outputSubDir()
    }
}

open class LibfdxWebTargetExtension @Inject constructor(
    private val targetName: String,
    objects: ObjectFactory
) : Named {
    val defaultPath: Property<String> = objects.property(String::class.java).convention("/")
    val buildDescription: Property<String> = objects.property(String::class.java)
    val runDescription: Property<String> = objects.property(String::class.java)

    override fun getName(): String {
        return targetName
    }
}

@Suppress("UNCHECKED_CAST")
open class LibfdxJsExtension @Inject constructor(
    objects: ObjectFactory,
    project: Project,
    private val jsConfig: TeaVMJSConfiguration
) : LibfdxWebExtension(objects, project, jsConfig) {
    init {
        outputDir.convention(project.layout.buildDirectory.dir("dist/web-js"))
        relativePathInOutputDir.convention("webapp")
        targetFileName.convention("app.js")
        optimization.convention(OptimizationLevel.BALANCED)
        debugInformation.convention(false)
        fastGlobalAnalysis.convention(false)
        outOfProcess.convention(false)
        processMemory.convention(512)
        obfuscated.convention(true)
        strict.convention(false)
        sourceMap.convention(false)
        sourceFilePolicy.convention(SourceFilePolicy.LINK_LOCAL_FILES)
    }

    override val entryPointName: Property<String>
        get() = jsConfig.entryPointName as Property<String>

    val targetFileName: Property<String>
        get() = jsConfig.targetFileName as Property<String>

    val obfuscated: Property<Boolean>
        get() = jsConfig.obfuscated as Property<Boolean>

    val strict: Property<Boolean>
        get() = jsConfig.strict as Property<Boolean>

    val sourceMap: Property<Boolean>
        get() = jsConfig.sourceMap as Property<Boolean>

    val sourceFilePolicy: Property<SourceFilePolicy>
        get() = jsConfig.sourceFilePolicy as Property<SourceFilePolicy>
}

@Suppress("UNCHECKED_CAST")
open class LibfdxWasmExtension @Inject constructor(
    objects: ObjectFactory,
    project: Project,
    private val wasmConfig: TeaVMWasmGCConfiguration
) : LibfdxWebExtension(objects, project, wasmConfig) {
    init {
        outputDir.convention(project.layout.buildDirectory.dir("dist/web-wasm"))
        relativePathInOutputDir.convention("webapp")
        targetFileName.convention("app.wasm")
        optimization.convention(OptimizationLevel.AGGRESSIVE)
        debugInformation.convention(false)
        fastGlobalAnalysis.convention(false)
        outOfProcess.convention(false)
        processMemory.convention(512)
        obfuscated.convention(true)
        strict.convention(false)
        copyRuntime.convention(true)
        modularRuntime.convention(false)
        sourceMap.convention(false)
        sourceFilePolicy.convention(SourceFilePolicy.LINK_LOCAL_FILES)
    }

    val targetFileName: Property<String>
        get() = wasmConfig.targetFileName as Property<String>

    val obfuscated: Property<Boolean>
        get() = wasmConfig.obfuscated as Property<Boolean>

    val strict: Property<Boolean>
        get() = wasmConfig.strict as Property<Boolean>

    val copyRuntime: Property<Boolean>
        get() = wasmConfig.copyRuntime as Property<Boolean>

    val modularRuntime: Property<Boolean>
        get() = wasmConfig.modularRuntime as Property<Boolean>

    val sourceMap: Property<Boolean>
        get() = wasmConfig.sourceMap as Property<Boolean>

    val sourceFilePolicy: Property<SourceFilePolicy>
        get() = wasmConfig.sourceFilePolicy as Property<SourceFilePolicy>
}

open class LibfdxDesktopCExtension @Inject constructor(
    project: Project,
    objects: ObjectFactory
) {
    val outputDir: DirectoryProperty = objects.directoryProperty()
        .convention(project.layout.buildDirectory.dir("dist/desktop-c"))
    val mainClass: Property<String> = objects.property(String::class.java)
    val relativePathInOutputDir: Property<String> = objects.property(String::class.java).convention("c/src")
    val optimization: Property<OptimizationLevel> = objects.property(OptimizationLevel::class.java)
        .convention(OptimizationLevel.AGGRESSIVE)
    val debugInformation: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val fastGlobalAnalysis: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val outOfProcess: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val processMemory: Property<Int> = objects.property(Int::class.javaObjectType).convention(512)
    val targetFileName: Property<String> = objects.property(String::class.java).convention("app")
    val buildType: Property<String> = objects.property(String::class.java).convention("Debug")
    val showConsole: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(project.providers.gradleProperty("libfdx.desktopC.showConsole")
            .map { value -> value.toBooleanStrictOrNull() ?: value.toBoolean() }
            .orElse(true))
    val openConsole: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(project.providers.gradleProperty("libfdx.desktopC.openConsole")
            .map { value -> value.toBooleanStrictOrNull() ?: value.toBoolean() }
            .orElse(true))
    val releasePath: DirectoryProperty = objects.directoryProperty()
        .convention(outputDir.map { it.dir("c/release") })
    val minHeapSize: Property<Int> = objects.property(Int::class.javaObjectType).convention(4)
    val maxHeapSize: Property<Int> = objects.property(Int::class.javaObjectType).convention(128)
    val heapDump: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val shortFileNames: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val obfuscated: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val targets: NamedDomainObjectContainer<LibfdxDesktopCTargetExtension> =
        objects.domainObjectContainer(LibfdxDesktopCTargetExtension::class.java) { name ->
            objects.newInstance(LibfdxDesktopCTargetExtension::class.java, name, objects)
        }

    fun target(name: String, action: Action<in LibfdxDesktopCTargetExtension>) {
        targets.create(name, action)
    }

    internal fun generatedSourcesDir(): Provider<Directory> {
        return outputDir.flatMap { output ->
            relativePathInOutputDir.map { relativePath -> output.dir(relativePath) }
        }
    }
}

open class LibfdxDesktopCTargetExtension @Inject constructor(
    private val targetName: String,
    objects: ObjectFactory
) : Named {
    val mainClass: Property<String> = objects.property(String::class.java)
    val targetFileName: Property<String> = objects.property(String::class.java).convention(targetName)
    val displayName: Property<String> = objects.property(String::class.java).convention(targetName)

    override fun getName(): String {
        return targetName
    }
}

open class LibfdxPspExtension @Inject constructor(
    project: Project,
    objects: ObjectFactory
) {
    val outputDir: DirectoryProperty = objects.directoryProperty()
        .convention(project.layout.buildDirectory.dir("dist/psp"))
    val mainClass: Property<String> = objects.property(String::class.java)
    val relativePathInOutputDir: Property<String> = objects.property(String::class.java).convention("c/src")
    val optimization: Property<OptimizationLevel> = objects.property(OptimizationLevel::class.java)
        .convention(OptimizationLevel.BALANCED)
    val debugInformation: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val fastGlobalAnalysis: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val outOfProcess: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val processMemory: Property<Int> = objects.property(Int::class.javaObjectType).convention(512)
    val targetFileName: Property<String> = objects.property(String::class.java).convention("app")
    val debugMemory: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(project.providers.gradleProperty("libfdx.psp.debugMemory")
            .map { value -> value.toBooleanStrictOrNull() ?: value.toBoolean() }
            .orElse(false))
    val ppssppExecutable: Property<String> = objects.property(String::class.java)
        .convention(project.providers.gradleProperty("libfdx.psp.ppssppExecutable")
            .orElse(project.providers.environmentVariable("PPSSPP_EXECUTABLE"))
            .orElse(""))
    val ppssppCaptureDelaySeconds: Property<Int> = objects.property(Int::class.javaObjectType)
        .convention(project.providers.gradleProperty("libfdx.psp.ppssppCaptureDelaySeconds")
            .map(String::toInt)
            .orElse(6))
    val ppssppAutoDownload: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(project.providers.gradleProperty("libfdx.psp.ppssppAutoDownload")
            .map { value -> value.toBooleanStrictOrNull() ?: value.toBoolean() }
            .orElse(true))
    val ppssppDownloadUrl: Property<String> = objects.property(String::class.java)
        .convention(project.providers.gradleProperty("libfdx.psp.ppssppDownloadUrl")
            .orElse(project.providers.environmentVariable("PPSSPP_DOWNLOAD_URL"))
            .orElse("https://www.ppsspp.org/files/1_20_4/ppsspp_win.zip"))
    val releasePath: DirectoryProperty = objects.directoryProperty()
        .convention(outputDir.map { it.dir("c/release") })
    val minHeapSize: Property<Int> = objects.property(Int::class.javaObjectType).convention(2)
    val maxHeapSize: Property<Int> = objects.property(Int::class.javaObjectType).convention(8)
    val heapDump: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val shortFileNames: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val obfuscated: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val targets: NamedDomainObjectContainer<LibfdxPspTargetExtension> =
        objects.domainObjectContainer(LibfdxPspTargetExtension::class.java) { name ->
            objects.newInstance(LibfdxPspTargetExtension::class.java, name, objects)
        }

    fun target(name: String, action: Action<in LibfdxPspTargetExtension>) {
        targets.create(name, action)
    }

    internal fun generatedSourcesDir(): Provider<Directory> {
        return outputDir.flatMap { output ->
            relativePathInOutputDir.map { relativePath -> output.dir(relativePath) }
        }
    }
}

open class LibfdxPspTargetExtension @Inject constructor(
    private val targetName: String,
    objects: ObjectFactory
) : Named {
    val mainClass: Property<String> = objects.property(String::class.java)
    val targetFileName: Property<String> = objects.property(String::class.java).convention(targetName)
    val displayName: Property<String> = objects.property(String::class.java).convention(targetName)

    override fun getName(): String {
        return targetName
    }
}

open class LibfdxIosCExtension @Inject constructor(
    project: Project,
    objects: ObjectFactory
) {
    val outputDir: DirectoryProperty = objects.directoryProperty()
        .convention(project.layout.buildDirectory.dir("dist/ios-c"))
    val mainClass: Property<String> = objects.property(String::class.java)
    val relativePathInOutputDir: Property<String> = objects.property(String::class.java).convention("c/src")
    val optimization: Property<OptimizationLevel> = objects.property(OptimizationLevel::class.java)
        .convention(OptimizationLevel.BALANCED)
    val debugInformation: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val fastGlobalAnalysis: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val outOfProcess: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val processMemory: Property<Int> = objects.property(Int::class.javaObjectType).convention(512)
    val targetFileName: Property<String> = objects.property(String::class.java).convention("app")
    val releasePath: DirectoryProperty = objects.directoryProperty()
        .convention(outputDir.map { it.dir("c/release") })
    val xcodeProjectDir: DirectoryProperty = objects.directoryProperty()
        .convention(outputDir.map { it.dir("xcode") })
    val bundleIdentifier: Property<String> = objects.property(String::class.java)
        .convention(project.providers.gradleProperty("libfdx.iosC.bundleIdentifier")
            .orElse("io.github.libfdx.iosc.app"))
    val graphicsApi: Property<String> = objects.property(String::class.java)
        .convention(project.providers.gradleProperty("libfdx.iosC.graphicsApi")
            .orElse("gles"))
    val minHeapSize: Property<Int> = objects.property(Int::class.javaObjectType).convention(4)
    val maxHeapSize: Property<Int> = objects.property(Int::class.javaObjectType).convention(128)
    val heapDump: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val shortFileNames: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val obfuscated: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val targets: NamedDomainObjectContainer<LibfdxIosCTargetExtension> =
        objects.domainObjectContainer(LibfdxIosCTargetExtension::class.java) { name ->
            objects.newInstance(LibfdxIosCTargetExtension::class.java, name, objects)
        }

    fun target(name: String, action: Action<in LibfdxIosCTargetExtension>) {
        targets.create(name, action)
    }

    internal fun generatedSourcesDir(): Provider<Directory> {
        return outputDir.flatMap { output ->
            relativePathInOutputDir.map { relativePath -> output.dir(relativePath) }
        }
    }
}

open class LibfdxIosCTargetExtension @Inject constructor(
    private val targetName: String,
    objects: ObjectFactory
) : Named {
    val mainClass: Property<String> = objects.property(String::class.java)
    val targetFileName: Property<String> = objects.property(String::class.java).convention(targetName)
    val displayName: Property<String> = objects.property(String::class.java).convention(targetName)
    val bundleIdentifier: Property<String> = objects.property(String::class.java)
    val graphicsApi: Property<String> = objects.property(String::class.java)

    override fun getName(): String {
        return targetName
    }
}

internal enum class LibfdxTarget {
    JS,
    WASM,
    DESKTOP_JVM,
    DESKTOP_C,
    IOS_C,
    PSP
}
