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
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
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
    private val cConfig: TeaVMCConfiguration
) {
    internal val declaredTargets = linkedSetOf<LibfdxTarget>()

    val assets: ConfigurableFileCollection = project.files()
    val bitmapFonts: NamedDomainObjectContainer<LibfdxBitmapFontExtension> =
        objects.domainObjectContainer(LibfdxBitmapFontExtension::class.java) { name ->
            objects.newInstance(LibfdxBitmapFontExtension::class.java, name)
        }

    val js: LibfdxJsExtension by lazy {
        objects.newInstance(LibfdxJsExtension::class.java, project, jsConfig)
    }

    val wasm: LibfdxWasmExtension by lazy {
        objects.newInstance(LibfdxWasmExtension::class.java, project, wasmConfig)
    }

    val desktopNative: LibfdxDesktopNativeExtension by lazy {
        objects.newInstance(LibfdxDesktopNativeExtension::class.java, project, cConfig)
    }

    val psp: LibfdxPspExtension by lazy {
        objects.newInstance(LibfdxPspExtension::class.java, project, cConfig)
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

    fun js(action: Action<in LibfdxJsExtension>) {
        declaredTargets.add(LibfdxTarget.JS)
        action.execute(js)
    }

    fun wasm(action: Action<in LibfdxWasmExtension>) {
        declaredTargets.add(LibfdxTarget.WASM)
        action.execute(wasm)
    }

    fun desktopNative(action: Action<in LibfdxDesktopNativeExtension>) {
        declaredTargets.add(LibfdxTarget.DESKTOP_NATIVE)
        action.execute(desktopNative)
    }

    fun psp(action: Action<in LibfdxPspExtension>) {
        declaredTargets.add(LibfdxTarget.PSP)
        action.execute(psp)
    }

    internal fun isDeclared(target: LibfdxTarget): Boolean {
        return declaredTargets.contains(target)
    }
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
    objects: ObjectFactory,
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

    internal fun webappDir(): Provider<Directory> {
        return outputSubDir()
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

@Suppress("UNCHECKED_CAST")
open class LibfdxDesktopNativeExtension @Inject constructor(
    objects: ObjectFactory,
    project: Project,
    private val cConfig: TeaVMCConfiguration
) : LibfdxTargetExtension(cConfig) {
    val targetFileName: Property<String> = objects.property(String::class.java).convention("app")
    val buildType: Property<String> = objects.property(String::class.java).convention("Debug")
    val showConsole: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(project.providers.gradleProperty("libfdx.desktopNative.showConsole")
            .map { value -> value.toBooleanStrictOrNull() ?: value.toBoolean() }
            .orElse(true))
    val openConsole: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(project.providers.gradleProperty("libfdx.desktopNative.openConsole")
            .map { value -> value.toBooleanStrictOrNull() ?: value.toBoolean() }
            .orElse(true))
    val releasePath: DirectoryProperty = objects.directoryProperty()
        .convention(outputDir.map { it.dir("c/release") })

    init {
        outputDir.convention(project.layout.buildDirectory.dir("dist/desktop-native"))
        relativePathInOutputDir.convention("c/src")
        targetFileName.convention("app")
        optimization.convention(OptimizationLevel.AGGRESSIVE)
        debugInformation.convention(false)
        fastGlobalAnalysis.convention(false)
        outOfProcess.convention(false)
        processMemory.convention(512)
        minHeapSize.convention(4)
        maxHeapSize.convention(128)
        heapDump.convention(false)
        shortFileNames.convention(true)
        obfuscated.convention(true)
    }

    val minHeapSize: Property<Int>
        get() = cConfig.minHeapSize as Property<Int>

    val maxHeapSize: Property<Int>
        get() = cConfig.maxHeapSize as Property<Int>

    val heapDump: Property<Boolean>
        get() = cConfig.heapDump as Property<Boolean>

    val shortFileNames: Property<Boolean>
        get() = cConfig.shortFileNames as Property<Boolean>

    val obfuscated: Property<Boolean>
        get() = cConfig.obfuscated as Property<Boolean>

    internal fun generatedSourcesDir(): Provider<Directory> {
        return outputSubDir()
    }
}

@Suppress("UNCHECKED_CAST")
open class LibfdxPspExtension @Inject constructor(
    objects: ObjectFactory,
    project: Project,
    private val cConfig: TeaVMCConfiguration
) : LibfdxTargetExtension(cConfig) {
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

    init {
        outputDir.convention(project.layout.buildDirectory.dir("dist/psp"))
        relativePathInOutputDir.convention("c/src")
        targetFileName.convention("app")
        optimization.convention(OptimizationLevel.BALANCED)
        debugInformation.convention(false)
        fastGlobalAnalysis.convention(false)
        outOfProcess.convention(false)
        processMemory.convention(512)
        minHeapSize.convention(2)
        maxHeapSize.convention(8)
        heapDump.convention(false)
        shortFileNames.convention(true)
        obfuscated.convention(false)
    }

    val minHeapSize: Property<Int>
        get() = cConfig.minHeapSize as Property<Int>

    val maxHeapSize: Property<Int>
        get() = cConfig.maxHeapSize as Property<Int>

    val heapDump: Property<Boolean>
        get() = cConfig.heapDump as Property<Boolean>

    val shortFileNames: Property<Boolean>
        get() = cConfig.shortFileNames as Property<Boolean>

    val obfuscated: Property<Boolean>
        get() = cConfig.obfuscated as Property<Boolean>

    internal fun generatedSourcesDir(): Provider<Directory> {
        return outputSubDir()
    }
}

internal enum class LibfdxTarget {
    JS,
    WASM,
    DESKTOP_NATIVE,
    PSP
}
