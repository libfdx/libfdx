import io.github.libfdx.build.LibExt
import org.gradle.api.GradleException
import org.gradle.api.tasks.Exec
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("java-library")
}

group = "${LibExt.fdxGroup}.runtime.fdx"

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("fdx_web")
}

val freetypeVersion = "2.14.3"
val freetypeSourceDir =
    rootProject.layout.projectDirectory.dir("libfdx/runtime/fdx/platform/shared/build/third-party/freetype/freetype-$freetypeVersion")
val runtimeFdxNativeDir = rootProject.layout.projectDirectory.dir("libfdx/runtime/fdx/platform/shared/src/main/cpp/runtime_fdx")
val shaderCompilerSourceDir =
    rootProject.layout.projectDirectory.dir("libfdx/runtime/fdx/platform/shared/src/main/cpp/shader_compiler")
val shaderCompilerDawnSourceDir =
    project(":libfdx:runtime:fdx:platform:shared").layout.buildDirectory.dir("third-party/dawn/source")
val runtimeFdxWebCmakeDir = layout.projectDirectory.dir("src/main/cpp")
val runtimeFdxWebBuildDir = layout.buildDirectory.dir("emscripten/freetype")
val runtimeFdxWebGeneratedResources = layout.buildDirectory.dir("generated/resources/runtimeFdxWeb")

val runtimeFdxShaderCompilerEnabled = providers.gradleProperty("libfdx.runtimeFdx.shaderCompiler")
    .map(String::toBoolean)
    .orElse(true)
val nativeBuildParallelism = providers.gradleProperty("libfdx.nativeBuildParallelism")
    .map(String::toInt)
    .orElse(Runtime.getRuntime().availableProcessors().coerceAtMost(8).coerceAtLeast(1))

fun executableCommand(name: String): List<String> {
    val windows = System.getProperty("os.name").lowercase().contains("win")
    if (!windows) {
        return listOf(name)
    }
    val searchDirectories = buildList {
        val path = System.getenv("PATH")
        if (path != null) {
            path.split(File.pathSeparatorChar).forEach { entry ->
                val directory = entry.trim().trim('"')
                if (directory.isNotEmpty()) {
                    add(directory)
                }
            }
        }
        val emsdk = System.getenv("EMSDK")?.trim()?.trim('"')
        if (!emsdk.isNullOrEmpty()) {
            add(File(emsdk, "upstream/emscripten").absolutePath)
        }
    }
    val candidates = listOf("$name.exe", "$name.bat", "$name.cmd", "$name.ps1", name)
    for (directory in searchDirectories) {
        for (candidate in candidates) {
            val file = File(directory, candidate)
            if (file.isFile) {
                return if (file.extension.equals("ps1", ignoreCase = true)) {
                    listOf("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", file.absolutePath)
                } else {
                    listOf(file.absolutePath)
                }
            }
        }
    }
    return listOf(name)
}

fun emsdkBundledPython(): File? {
    if (!System.getenv("EMSDK_PYTHON").isNullOrBlank()) {
        return null
    }
    val emsdk = System.getenv("EMSDK")?.trim()?.trim('"')
    if (emsdk.isNullOrEmpty()) {
        return null
    }
    val pythonDir = File(emsdk, "python")
    return pythonDir.listFiles()
        ?.map { File(it, "python.exe") }
        ?.firstOrNull(File::isFile)
}

fun Exec.useEmsdkBundledPython() {
    emsdkBundledPython()?.let { python ->
        environment("EMSDK_PYTHON", python.absolutePath)
    }
}

fun deleteUnexpectedWebRuntimeArtifacts(directory: File) {
    if (!directory.isDirectory) {
        return
    }
    val expected = setOf("fdx.js", "fdx.wasm")
    directory.listFiles()?.forEach { file ->
        if (file.isFile && file.name !in expected
                && (file.extension.equals("js", ignoreCase = true)
                || file.extension.equals("wasm", ignoreCase = true))) {
            file.delete()
        }
    }
}

fun hasValidEmscriptenCmakeCache(directory: File): Boolean {
    val cache = File(directory, "CMakeCache.txt")
    if (!cache.isFile) {
        return false
    }
    val text = cache.readText()
    val hasEmscriptenToolchain = text.contains("Emscripten.cmake")
    val hasEmscriptenCCompiler = text.lineSequence().any { line ->
        line.startsWith("CMAKE_C_COMPILER:") && line.contains("emcc")
    }
    val hasEmscriptenCxxCompiler = text.lineSequence().any { line ->
        line.startsWith("CMAKE_CXX_COMPILER:") && line.contains("em++")
    }
    return hasEmscriptenToolchain && hasEmscriptenCCompiler && hasEmscriptenCxxCompiler
}

fun runtimeFdxShaderCompilerCmakeArgs(): List<String> {
    if (!runtimeFdxShaderCompilerEnabled.get()) {
        return listOf("-DLIBFDX_ENABLE_SHADER_COMPILER=OFF")
    }
    val args = mutableListOf(
        "-DLIBFDX_ENABLE_SHADER_COMPILER=ON",
        "-DLIBFDX_SHADERC_SOURCE_DIR=${shaderCompilerSourceDir.asFile.absolutePath}",
        "-DFDX_DAWN_SOURCE_DIR=${shaderCompilerDawnSourceDir.get().asFile.absolutePath}"
    )
    emsdkBundledPython()?.let { python ->
        args += "-DPython3_EXECUTABLE=${python.absolutePath}"
    }
    return args
}

fun Task.runtimeFdxShaderCompilerDependency() {
    if (runtimeFdxShaderCompilerEnabled.get()) {
        dependsOn(":libfdx:runtime:fdx:platform:shared:resolve_runtime_fdx_tint_source")
    }
}

fun Task.runtimeFdxNativeSourceInputs() {
    inputs.dir(runtimeFdxNativeDir)
    if (runtimeFdxShaderCompilerEnabled.get()) {
        inputs.dir(shaderCompilerSourceDir)
    }
}

sourceSets {
    named("main") {
        resources.srcDir(runtimeFdxWebGeneratedResources)
    }
}

val configureRuntimeFdxWebNative = tasks.register<Exec>("configure_runtime_fdx_web_native") {
    group = "libfdx native"
    description = "Configures the Emscripten runtime fdx web native build."
    dependsOn(":libfdx:runtime:fdx:platform:shared:extract_freetype_source")
    runtimeFdxShaderCompilerDependency()
    inputs.file(runtimeFdxWebCmakeDir.file("CMakeLists.txt"))
    inputs.property("runtimeFdxShaderCompilerEnabled", runtimeFdxShaderCompilerEnabled)
    outputs.dir(runtimeFdxWebBuildDir)
    outputs.upToDateWhen {
        hasValidEmscriptenCmakeCache(runtimeFdxWebBuildDir.get().asFile)
    }
    doFirst {
        val buildDir = runtimeFdxWebBuildDir.get().asFile
        if (buildDir.exists() && !hasValidEmscriptenCmakeCache(buildDir)) {
            buildDir.deleteRecursively()
        }
        useEmsdkBundledPython()
        buildDir.mkdirs()
        runtimeFdxWebGeneratedResources.get().asFile.mkdirs()
    }
    commandLine(executableCommand("emcmake") + listOf(
        "cmake",
        "-S", runtimeFdxWebCmakeDir.asFile.absolutePath,
        "-B", runtimeFdxWebBuildDir.get().asFile.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DLIBFDX_FREETYPE_SOURCE_DIR=${freetypeSourceDir.asFile.absolutePath}",
        "-DLIBFDX_RUNTIME_FDX_NATIVE_DIR=${runtimeFdxNativeDir.asFile.absolutePath}",
        "-DLIBFDX_WEB_OUTPUT_DIR=${runtimeFdxWebGeneratedResources.get().asFile.absolutePath}"
    ) + runtimeFdxShaderCompilerCmakeArgs())
}

val buildRuntimeFdxWebNative = tasks.register<Exec>("build_runtime_fdx_web_native") {
    group = "libfdx native"
    description = "Builds fdx.js and fdx.wasm for runtime fdx web support."
    dependsOn(configureRuntimeFdxWebNative)
    runtimeFdxNativeSourceInputs()
    inputs.file(runtimeFdxWebCmakeDir.file("CMakeLists.txt"))
    inputs.property("runtimeFdxShaderCompilerEnabled", runtimeFdxShaderCompilerEnabled)
    outputs.file(runtimeFdxWebGeneratedResources.map { it.file("fdx.js") })
    outputs.file(runtimeFdxWebGeneratedResources.map { it.file("fdx.wasm") })
    doFirst {
        useEmsdkBundledPython()
        deleteUnexpectedWebRuntimeArtifacts(runtimeFdxWebGeneratedResources.get().asFile)
    }
    commandLine(executableCommand("cmake") + listOf(
        "--build", runtimeFdxWebBuildDir.get().asFile.absolutePath,
        "--config", "Release",
        "--parallel", nativeBuildParallelism.get().toString()
    ))
}

tasks.named<ProcessResources>("processResources") {
    mustRunAfter(buildRuntimeFdxWebNative)
    doFirst {
        deleteUnexpectedWebRuntimeArtifacts(destinationDir)
    }
}

tasks.register("generate_runtime_fdx_web_native") {
    group = "libfdx native"
    description = "Generates runtime fdx web native resources in fdx_web generated resources."
    dependsOn(buildRuntimeFdxWebNative)
}

tasks.register("validate_runtime_fdx_web_native_resources") {
    group = "libfdx native"
    description = "Validates generated fdx_web native resources before packaging."
    doLast {
        val missing = listOf("fdx.js", "fdx.wasm")
            .map { runtimeFdxWebGeneratedResources.get().asFile.resolve(it) }
            .filterNot(File::isFile)
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing generated fdx_web native resources:\n" +
                        missing.joinToString(separator = "\n") { " - ${it.absolutePath}" } + "\n" +
                        "Run :libfdx:runtime:fdx:platform:web:generate_runtime_fdx_web_native first."
            )
        }
    }
}
