import org.gradle.api.GradleException
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("fdx_desktop")
}

val runtimeFdxDesktopGeneratedResources = layout.buildDirectory.dir("generated/resources/runtimeFdxDesktop")
val runtimeFdxNativeDir = rootProject.layout.projectDirectory.dir("libfdx/runtime/fdx/platform/shared/src/main/cpp/runtime_fdx")
val generatedDesktopRuntimeFdxNatives = mapOf(
    "windows-x64" to "fdx.dll",
    "linux-x64" to "libfdx.so",
    "macos-x64" to "libfdx.dylib",
    "macos-arm64" to "libfdx.dylib"
)
val requireAllRuntimeFdxNatives = providers.gradleProperty("libfdx.runtimeFdx.requireAllNativeResources")
    .map(String::toBoolean)
    .orElse(false)

fun executableCommand(name: String): List<String> {
    val windows = System.getProperty("os.name").lowercase().contains("win")
    if (!windows) {
        return listOf(name)
    }
    val path = System.getenv("PATH") ?: return listOf(name)
    val candidates = listOf("$name.exe", "$name.bat", "$name.cmd", "$name.ps1", name)
    for (entry in path.split(File.pathSeparatorChar)) {
        val directory = entry.trim().trim('"')
        if (directory.isEmpty()) {
            continue
        }
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

fun runtimeFdxHostDesktopClassifier(): String? {
    val os = System.getProperty("os.name", "").lowercase()
    val arch = System.getProperty("os.arch", "").lowercase()
    val archPart = if (arch.contains("aarch64") || arch.contains("arm64")) {
        "arm64"
    } else {
        "x64"
    }
    return when {
        os.contains("windows") -> "windows-x64"
        os.contains("linux") && archPart == "x64" -> "linux-x64"
        os.contains("mac") || os.contains("darwin") -> "macos-$archPart"
        else -> null
    }
}

fun requireWindowsHost() {
    if (!System.getProperty("os.name").lowercase().contains("windows")) {
        throw GradleException("Runtime fdx Windows native artifacts must be built on Windows.")
    }
}

fun requireLinuxHost() {
    if (!System.getProperty("os.name").lowercase().contains("linux")) {
        throw GradleException("Runtime fdx Linux native artifacts must be built on Linux.")
    }
}

fun macosClassifier(): String {
    val arch = System.getProperty("os.arch").lowercase()
    return if (arch.contains("aarch64") || arch.contains("arm64")) {
        "macos-arm64"
    } else {
        "macos-x64"
    }
}

fun requireMacosHost() {
    val os = System.getProperty("os.name").lowercase()
    if (!os.contains("mac") && !os.contains("darwin")) {
        throw GradleException("Runtime fdx macOS native artifacts must be built on macOS.")
    }
}

val runtimeFdxWindowsBuildDir = layout.buildDirectory.dir("cmake/runtimeFdx/windows")
val runtimeFdxWindowsOutputDir = layout.buildDirectory.dir("native/runtimeFdx/windows")
val runtimeFdxWindowsNativeLibrary = runtimeFdxWindowsOutputDir.map { it.file("fdx.dll") }

val configureRuntimeFdxWindowsNative = tasks.register<Exec>("configure_runtime_fdx_windows_native") {
    group = "libfdx native"
    description = "Configures the Windows runtime fdx native library."
    outputs.dir(runtimeFdxWindowsBuildDir)
    doFirst {
        requireWindowsHost()
        runtimeFdxWindowsBuildDir.get().asFile.mkdirs()
        runtimeFdxWindowsOutputDir.get().asFile.mkdirs()
    }
    commandLine(executableCommand("cmake") + listOf(
        "-S", runtimeFdxNativeDir.asFile.absolutePath,
        "-B", runtimeFdxWindowsBuildDir.get().asFile.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DLIBFDX_JAVA_HOME=${System.getProperty("java.home")}",
        "-DLIBFDX_DESKTOP_OUTPUT_DIR=${runtimeFdxWindowsOutputDir.get().asFile.absolutePath}"
    ))
}

val buildRuntimeFdxWindowsNative = tasks.register<Exec>("build_runtime_fdx_windows_native") {
    group = "libfdx native"
    description = "Builds fdx.dll for Windows runtime fdx."
    dependsOn(configureRuntimeFdxWindowsNative)
    outputs.file(runtimeFdxWindowsNativeLibrary)
    doFirst {
        requireWindowsHost()
    }
    commandLine(executableCommand("cmake") + listOf(
        "--build", runtimeFdxWindowsBuildDir.get().asFile.absolutePath,
        "--config", "Release"
    ))
}

val generateRuntimeFdxWindowsNative = tasks.register<Copy>("generate_runtime_fdx_windows_native") {
    group = "libfdx native"
    description = "Generates fdx.dll in fdx_desktop generated resources."
    dependsOn(buildRuntimeFdxWindowsNative)
    from(runtimeFdxWindowsNativeLibrary)
    into(runtimeFdxDesktopGeneratedResources.map { it.dir("libfdx-native/desktop/windows-x64") })
}

val runtimeFdxLinuxBuildDir = layout.buildDirectory.dir("cmake/runtimeFdx/linux")
val runtimeFdxLinuxOutputDir = layout.buildDirectory.dir("native/runtimeFdx/linux")
val runtimeFdxLinuxNativeLibrary = runtimeFdxLinuxOutputDir.map { it.file("libfdx.so") }

val configureRuntimeFdxLinuxNative = tasks.register<Exec>("configure_runtime_fdx_linux_native") {
    group = "libfdx native"
    description = "Configures the Linux runtime fdx native library."
    outputs.dir(runtimeFdxLinuxBuildDir)
    doFirst {
        requireLinuxHost()
        runtimeFdxLinuxBuildDir.get().asFile.mkdirs()
        runtimeFdxLinuxOutputDir.get().asFile.mkdirs()
    }
    commandLine(executableCommand("cmake") + listOf(
        "-S", runtimeFdxNativeDir.asFile.absolutePath,
        "-B", runtimeFdxLinuxBuildDir.get().asFile.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DLIBFDX_JAVA_HOME=${System.getProperty("java.home")}",
        "-DLIBFDX_DESKTOP_OUTPUT_DIR=${runtimeFdxLinuxOutputDir.get().asFile.absolutePath}"
    ))
}

val buildRuntimeFdxLinuxNative = tasks.register<Exec>("build_runtime_fdx_linux_native") {
    group = "libfdx native"
    description = "Builds libfdx.so for Linux runtime fdx."
    dependsOn(configureRuntimeFdxLinuxNative)
    outputs.file(runtimeFdxLinuxNativeLibrary)
    doFirst {
        requireLinuxHost()
    }
    commandLine(executableCommand("cmake") + listOf(
        "--build", runtimeFdxLinuxBuildDir.get().asFile.absolutePath,
        "--config", "Release"
    ))
}

val generateRuntimeFdxLinuxNative = tasks.register<Copy>("generate_runtime_fdx_linux_native") {
    group = "libfdx native"
    description = "Generates libfdx.so in fdx_desktop generated resources."
    dependsOn(buildRuntimeFdxLinuxNative)
    from(runtimeFdxLinuxNativeLibrary)
    into(runtimeFdxDesktopGeneratedResources.map { it.dir("libfdx-native/desktop/linux-x64") })
}

val runtimeFdxMacosBuildDir = layout.buildDirectory.dir("cmake/runtimeFdx/macos")
val runtimeFdxMacosOutputDir = layout.buildDirectory.dir("native/runtimeFdx/macos")
val runtimeFdxMacosNativeLibrary = runtimeFdxMacosOutputDir.map { it.file("libfdx.dylib") }

val configureRuntimeFdxMacosNative = tasks.register<Exec>("configure_runtime_fdx_macos_native") {
    group = "libfdx native"
    description = "Configures the macOS runtime fdx native library."
    outputs.dir(runtimeFdxMacosBuildDir)
    doFirst {
        requireMacosHost()
        runtimeFdxMacosBuildDir.get().asFile.mkdirs()
        runtimeFdxMacosOutputDir.get().asFile.mkdirs()
    }
    commandLine(executableCommand("cmake") + listOf(
        "-S", runtimeFdxNativeDir.asFile.absolutePath,
        "-B", runtimeFdxMacosBuildDir.get().asFile.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DLIBFDX_JAVA_HOME=${System.getProperty("java.home")}",
        "-DLIBFDX_DESKTOP_OUTPUT_DIR=${runtimeFdxMacosOutputDir.get().asFile.absolutePath}"
    ))
}

val buildRuntimeFdxMacosNative = tasks.register<Exec>("build_runtime_fdx_macos_native") {
    group = "libfdx native"
    description = "Builds libfdx.dylib for macOS runtime fdx."
    dependsOn(configureRuntimeFdxMacosNative)
    outputs.file(runtimeFdxMacosNativeLibrary)
    doFirst {
        requireMacosHost()
    }
    commandLine(executableCommand("cmake") + listOf(
        "--build", runtimeFdxMacosBuildDir.get().asFile.absolutePath,
        "--config", "Release"
    ))
}

val generateRuntimeFdxMacosNative = tasks.register<Copy>("generate_runtime_fdx_macos_native") {
    group = "libfdx native"
    description = "Generates libfdx.dylib in fdx_desktop generated resources."
    dependsOn(buildRuntimeFdxMacosNative)
    from(runtimeFdxMacosNativeLibrary)
    into(runtimeFdxDesktopGeneratedResources.map { it.dir("libfdx-native/desktop/${macosClassifier()}") })
}

sourceSets {
    named("main") {
        resources.srcDir(runtimeFdxDesktopGeneratedResources)
    }
}

tasks.named<ProcessResources>("processResources") {
    mustRunAfter(generateRuntimeFdxWindowsNative, generateRuntimeFdxLinuxNative, generateRuntimeFdxMacosNative)
}

tasks.register("validate_runtime_fdx_desktop_native_resources") {
    group = "libfdx native"
    description = "Validates generated fdx_desktop native resources before packaging."
    doLast {
        val desktopRoot = runtimeFdxDesktopGeneratedResources.get().asFile
        val missingRequiredFiles = mutableListOf<File>()
        val missingWarnedFiles = mutableListOf<File>()
        val requireAll = requireAllRuntimeFdxNatives.get()
        val hostDesktopClassifier = runtimeFdxHostDesktopClassifier()
        generatedDesktopRuntimeFdxNatives.forEach { (classifier, fileName) ->
            val file = desktopRoot.resolve("libfdx-native/desktop/$classifier/$fileName")
            if (!file.isFile) {
                if (requireAll || classifier == hostDesktopClassifier) {
                    missingRequiredFiles += file
                } else {
                    missingWarnedFiles += file
                }
            }
        }
        if (missingWarnedFiles.isNotEmpty()) {
            logger.warn(
                "Missing non-host fdx_desktop native resources; continuing because " +
                        "libfdx.runtimeFdx.requireAllNativeResources=false:\n" +
                        missingWarnedFiles.joinToString(separator = "\n") { " - ${it.absolutePath}" }
            )
        }
        if (missingRequiredFiles.isNotEmpty()) {
            throw GradleException(
                "Missing generated fdx_desktop native resources:\n" +
                        missingRequiredFiles.joinToString(separator = "\n") { " - ${it.absolutePath}" }
            )
        }
    }
}
