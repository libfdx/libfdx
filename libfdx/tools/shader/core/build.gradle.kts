import io.github.libfdx.build.LibExt
import java.util.Properties

plugins {
    id("java-library")
}

group = "${LibExt.fdxGroup}.tools.shader"

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("shader_compiler")
}

dependencies {
    api(project(":libfdx:graphics:api"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

val dawnSourceDirectory = layout.buildDirectory.dir("third-party/dawn/source")
val shadercOutputDirectory = layout.buildDirectory.dir("native/shaderc")

val cmakeCommand = providers.gradleProperty("libfdx.shaderc.cmake").orElse("cmake")
val emcmakeCommand = providers.gradleProperty("libfdx.shaderc.emcmake")
    .orElse(providers.environmentVariable("EMCMAKE"))
    .orElse("emcmake")
val dawnRepository = providers.gradleProperty("libfdx.shaderc.dawnRepository")
    .orElse("https://dawn.googlesource.com/dawn")
val dawnRevision = providers.gradleProperty("libfdx.shaderc.dawnRevision")
    .orElse("refs/heads/main")
val androidAbis = providers.gradleProperty("libfdx.shaderc.androidAbis")
    .map { value -> value.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() } }
    .orElse(listOf("arm64-v8a"))
val webDiagnostics = providers.gradleProperty("libfdx.shaderc.webDiagnostics")
    .map { it.toBoolean() }
    .orElse(false)

fun localProperty(name: String): String? {
    val file = rootProject.file("local.properties")
    if (!file.isFile) {
        return null
    }
    val properties = Properties()
    file.inputStream().use { properties.load(it) }
    return properties.getProperty(name)?.replace("\\:", ":")
}

fun androidSdkDirectory(): File {
    val value = providers.gradleProperty("libfdx.shaderc.androidSdk")
        .orElse(providers.environmentVariable("ANDROID_HOME"))
        .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
        .orNull
        ?: localProperty("sdk.dir")
        ?: throw GradleException("Android SDK not found. Set libfdx.shaderc.androidSdk, ANDROID_HOME, "
                + "ANDROID_SDK_ROOT, or sdk.dir in local.properties.")
    return file(value)
}

fun androidNdkDirectory(): File {
    val explicit = providers.gradleProperty("libfdx.shaderc.androidNdk")
        .orElse(providers.environmentVariable("ANDROID_NDK_ROOT"))
        .orNull
    if (!explicit.isNullOrBlank()) {
        return file(explicit)
    }
    val ndkRoot = androidSdkDirectory().resolve("ndk")
    return ndkRoot.listFiles()
        ?.filter { it.isDirectory }
        ?.sortedByDescending { it.name }
        ?.firstOrNull()
        ?: throw GradleException("Android NDK not found under ${ndkRoot.absolutePath}.")
}

fun ninjaInCmakeDirectory(directory: File): File? {
    val bin = directory.resolve("bin")
    return listOf("ninja", "ninja.exe")
        .map { bin.resolve(it) }
        .firstOrNull { it.isFile }
}

fun androidCmakeDirectory(): File {
    val explicit = providers.gradleProperty("libfdx.shaderc.androidCmake").orNull
    if (!explicit.isNullOrBlank()) {
        return file(explicit)
    }
    val cmakeRoot = androidSdkDirectory().resolve("cmake")
    return cmakeRoot.listFiles()
        ?.filter { it.isDirectory && ninjaInCmakeDirectory(it) != null }
        ?.sortedByDescending { it.name }
        ?.firstOrNull()
        ?: throw GradleException("Android SDK CMake/Ninja not found under ${cmakeRoot.absolutePath}.")
}

fun androidNinjaExecutable(): File {
    val cmake = androidCmakeDirectory()
    return ninjaInCmakeDirectory(cmake)
        ?: throw GradleException("Android SDK CMake Ninja executable not found under ${cmake.absolutePath}.")
}

fun resolvedScriptCommand(command: String): String {
    val hasPath = command.contains('/') || command.contains('\\')
    val file = file(command)
    if (hasPath || file.isFile) {
        return file.absolutePath
    }
    val pathEntries = System.getenv("PATH")
        ?.split(File.pathSeparatorChar)
        ?.filter { it.isNotBlank() }
        ?: emptyList()
    val extensions = if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
        listOf(".bat", ".cmd", ".exe", ".ps1", "")
    } else {
        listOf("")
    }
    for (entry in pathEntries) {
        for (extension in extensions) {
            val candidate = File(entry, command + extension)
            if (candidate.isFile) {
                return candidate.absolutePath
            }
        }
    }
    return command
}

fun scriptCommand(command: String, vararg args: String): List<String> {
    val resolvedCommand = resolvedScriptCommand(command)
    return if (resolvedCommand.endsWith(".ps1", ignoreCase = true)) {
        listOf("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", resolvedCommand) + args
    } else {
        listOf(resolvedCommand) + args
    }
}

fun ninjaExecutable(): File? {
    val explicit = providers.gradleProperty("libfdx.shaderc.ninja")
        .orElse(providers.environmentVariable("NINJA"))
        .orNull
    if (!explicit.isNullOrBlank()) {
        return file(explicit).takeIf { it.isFile }
    }
    val resolved = resolvedScriptCommand("ninja")
    if (resolved != "ninja") {
        val resolvedFile = file(resolved)
        if (resolvedFile.isFile) {
            return resolvedFile
        }
    }
    return runCatching { androidCmakeDirectory().resolve("bin/ninja.exe").takeIf { it.isFile } }.getOrNull()
}

fun ninjaCmakeArgs(): List<String> {
    val ninja = ninjaExecutable() ?: return emptyList()
    return listOf("-G", "Ninja", "-DCMAKE_MAKE_PROGRAM=${ninja.absolutePath}")
}

tasks.register("resolve_shaderc_dawn_source") {
    group = "libfdx native"
    description = "Downloads the pinned Dawn/Tint source used by libfdx_shaderc."
    outputs.dir(dawnSourceDirectory)
    doLast {
        val source = dawnSourceDirectory.get().asFile
        if (!source.resolve(".git").isDirectory) {
            source.parentFile.mkdirs()
            providers.exec {
                commandLine("git", "clone", "--depth", "1", "--filter=blob:none", "--sparse",
                        dawnRepository.get(), source.absolutePath)
            }.result.get()
            providers.exec {
                workingDir = source
                commandLine("git", "sparse-checkout", "set", "--no-cone", "CMakeLists.txt", "DEPS", "cmake", "src",
                        "include", "third_party", "build", "build_overrides", "generator", "tools")
            }.result.get()
        } else {
            providers.exec {
                workingDir = source
                commandLine("git", "config", "core.longpaths", "true")
            }.result.get()
            providers.exec {
                workingDir = source
                commandLine("git", "sparse-checkout", "set", "--no-cone", "CMakeLists.txt", "DEPS", "cmake", "src",
                        "include", "third_party", "build", "build_overrides", "generator", "tools")
            }.result.get()
        }
        providers.exec {
            workingDir = source
            commandLine("git", "fetch", "--depth", "1", "origin", dawnRevision.get())
        }.result.get()
        providers.exec {
            workingDir = source
            commandLine("git", "reset", "--hard", "FETCH_HEAD")
        }.result.get()
    }
}

fun configureCmakeTask(name: String, buildDirectory: Provider<Directory>, outputDirectory: Provider<Directory>,
        extraArgs: List<String> = emptyList(), emscripten: Boolean = false): TaskProvider<Exec> {
    return tasks.register<Exec>(name) {
        group = "libfdx native"
        dependsOn("resolve_shaderc_dawn_source")
        val args = listOf(
            "cmake",
            "-S", layout.projectDirectory.dir("src/main/cpp").asFile.absolutePath,
            "-B", buildDirectory.get().asFile.absolutePath,
            "-DFDX_DAWN_SOURCE_DIR=${dawnSourceDirectory.get().asFile.absolutePath}",
            "-DFDX_SHADERC_OUTPUT_DIR=${outputDirectory.get().asFile.absolutePath}",
            "-DCMAKE_BUILD_TYPE=Release"
        ) + extraArgs
        commandLine(if (emscripten) scriptCommand(emcmakeCommand.get(), *args.toTypedArray()) else args)
    }
}

fun buildCmakeTask(name: String, configureTask: TaskProvider<Exec>, buildDirectory: Provider<Directory>): TaskProvider<Exec> {
    return tasks.register<Exec>(name) {
        group = "libfdx native"
        dependsOn(configureTask)
        commandLine(cmakeCommand.get(), "--build", buildDirectory.get().asFile.absolutePath, "--config", "Release")
    }
}

val hostBuildDirectory = layout.buildDirectory.dir("cmake/shaderc/host")
val hostOutputDirectory = shadercOutputDirectory.map { it.dir("host") }
val configureHost = configureCmakeTask("configure_shaderc_host", hostBuildDirectory, hostOutputDirectory)
buildCmakeTask("build_shaderc_host", configureHost, hostBuildDirectory)

val webBuildDirectory = layout.buildDirectory.dir("cmake/shaderc/web")
val webOutputDirectory = shadercOutputDirectory.map { it.dir("web") }
val webCmakeArgs = ninjaCmakeArgs() + listOf(
    "-DFDX_SHADERC_WEB_DIAGNOSTICS=${if (webDiagnostics.get()) "ON" else "OFF"}"
)
val configureWeb = configureCmakeTask("configure_shaderc_web", webBuildDirectory, webOutputDirectory,
    webCmakeArgs, emscripten = true)
buildCmakeTask("build_shaderc_web", configureWeb, webBuildDirectory)

val androidBuildTasks = androidAbis.get().map { abi ->
    val safeAbi = abi.replace('-', '_')
    val buildDirectory = layout.buildDirectory.dir("cmake/shaderc/android/$abi")
    val outputDirectory = shadercOutputDirectory.map { it.dir("android/$abi") }
    val configure = configureCmakeTask(
        "configure_shaderc_android_$safeAbi",
        buildDirectory,
        outputDirectory,
        listOf(
            "-G", "Ninja",
            "-DCMAKE_MAKE_PROGRAM=${androidNinjaExecutable().absolutePath}",
            "-DCMAKE_TOOLCHAIN_FILE=${androidNdkDirectory().resolve("build/cmake/android.toolchain.cmake").absolutePath}",
            "-DANDROID_ABI=$abi",
            "-DANDROID_PLATFORM=android-23"
        )
    )
    buildCmakeTask("build_shaderc_android_$safeAbi", configure, buildDirectory)
}

tasks.register("build_shaderc_android") {
    group = "libfdx native"
    description = "Builds Android shader compiler JNI libraries."
    dependsOn(androidBuildTasks)
}
