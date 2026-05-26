import java.net.URI
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
    archivesName.set("runtime_core")
}

dependencies {
    api(project(":libfdx:foundation:core"))
}

val freetypeVersion = "2.14.3"
val freetypeArchiveName = "freetype-$freetypeVersion.tar.xz"
val freetypeSourceUrl = "https://download.savannah.gnu.org/releases/freetype/$freetypeArchiveName"
val freetypeArchive = layout.buildDirectory.file("third-party/downloads/$freetypeArchiveName")
val freetypeExtractDir = layout.buildDirectory.dir("third-party/freetype")
val freetypeSourceDir = freetypeExtractDir.map { it.dir("freetype-$freetypeVersion") }
val runtimeCoreNativeDir = layout.projectDirectory.dir("src/main/resources/libfdx-native/desktop/runtime_core")
val desktopRuntimeCoreBuildDir = layout.buildDirectory.dir("cmake/desktop/runtime_core")
val desktopRuntimeCoreOutputDir = layout.buildDirectory.dir("native/desktop/runtime_core")
val desktopRuntimeCoreGeneratedResources = layout.buildDirectory.dir("generated/resources/runtimeCoreDesktop")
val webFreetypeCmakeDir = layout.projectDirectory.dir("src/main/resources/libfdx-native/web/runtime_core")
val webFreetypeBuildDir = layout.buildDirectory.dir("emscripten/freetype")
val webFreetypeGeneratedResources = layout.buildDirectory.dir("generated/resources/runtimeCoreWeb")

fun deleteUnexpectedWebRuntimeArtifacts(directory: File) {
    if (!directory.isDirectory) {
        return
    }
    val expected = setOf("fdx.js", "fdx.wasm", "fdx-loader.js")
    directory.listFiles()?.forEach { file ->
        if (file.isFile && file.name !in expected
                && (file.extension.equals("js", ignoreCase = true)
                || file.extension.equals("wasm", ignoreCase = true))) {
            file.delete()
        }
    }
}

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

fun desktopNativeClassifier(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val osPart = when {
        os.contains("windows") -> "windows"
        os.contains("mac") || os.contains("darwin") -> "macos"
        os.contains("linux") -> "linux"
        else -> "unknown"
    }
    val archPart = if (arch.contains("aarch64") || arch.contains("arm64")) "arm64" else "x64"
    return "$osPart-$archPart"
}

fun desktopRuntimeCoreLibraryFileName(): String {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("windows") -> "fdx.dll"
        os.contains("mac") || os.contains("darwin") -> "libfdx.dylib"
        else -> "libfdx.so"
    }
}

val desktopRuntimeCoreNativeLibrary = desktopRuntimeCoreOutputDir.map {
    it.file(desktopRuntimeCoreLibraryFileName())
}

val downloadFreetypeSource = tasks.register("download_freetype_source") {
    group = "libfdx native"
    description = "Downloads FreeType source used to build runtime_core native font support."
    outputs.file(freetypeArchive)
    doLast {
        val output = freetypeArchive.get().asFile
        output.parentFile.mkdirs()
        if (!output.isFile) {
            URI(freetypeSourceUrl).toURL().openStream().use { input ->
                output.outputStream().use { outputStream ->
                    input.copyTo(outputStream)
                }
            }
        }
    }
}

val extractFreetypeSource = tasks.register<Exec>("extract_freetype_source") {
    group = "libfdx native"
    description = "Extracts FreeType source into build/third-party for native runtime_core builds."
    dependsOn(downloadFreetypeSource)
    outputs.dir(freetypeSourceDir)
    doFirst {
        val output = freetypeExtractDir.get().asFile
        output.mkdirs()
    }
    executable = "tar"
    args("-xf", freetypeArchive.get().asFile.absolutePath, "-C", freetypeExtractDir.get().asFile.absolutePath)
}

tasks.register("prepare_runtime_core_native_sources") {
    group = "libfdx native"
    description = "Prepares third-party native sources used by runtime_core native builds."
    dependsOn(extractFreetypeSource)
}

val configureDesktopRuntimeCoreNative = tasks.register<Exec>("configure_desktop_runtime_core_native") {
    group = "libfdx native"
    description = "Configures the desktop runtime_core native library used by optional math acceleration."
    outputs.dir(desktopRuntimeCoreBuildDir)
    doFirst {
        desktopRuntimeCoreBuildDir.get().asFile.mkdirs()
        desktopRuntimeCoreOutputDir.get().asFile.mkdirs()
    }
    commandLine(executableCommand("cmake") + listOf(
        "-S", runtimeCoreNativeDir.asFile.absolutePath,
        "-B", desktopRuntimeCoreBuildDir.get().asFile.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DLIBFDX_JAVA_HOME=${System.getProperty("java.home")}",
        "-DLIBFDX_DESKTOP_OUTPUT_DIR=${desktopRuntimeCoreOutputDir.get().asFile.absolutePath}"
    ))
}

val buildDesktopRuntimeCoreNative = tasks.register<Exec>("build_desktop_runtime_core_native") {
    group = "libfdx native"
    description = "Builds the desktop runtime_core native library used by optional math acceleration."
    dependsOn(configureDesktopRuntimeCoreNative)
    outputs.file(desktopRuntimeCoreNativeLibrary)
    commandLine(executableCommand("cmake") + listOf(
        "--build", desktopRuntimeCoreBuildDir.get().asFile.absolutePath,
        "--config", "Release"
    ))
}

tasks.register<Copy>("copy_desktop_runtime_core_native") {
    group = "libfdx native"
    description = "Copies the desktop runtime_core native library into generated runtime_core resources."
    dependsOn(buildDesktopRuntimeCoreNative)
    from(desktopRuntimeCoreNativeLibrary)
    into(desktopRuntimeCoreGeneratedResources.map {
        it.dir("libfdx-native/desktop/${desktopNativeClassifier()}")
    })
}

val configureWebFreetypeEmscripten = tasks.register<Exec>("configure_web_freetype_emscripten") {
    group = "libfdx native"
    description = "Configures the Emscripten FreeType build used by runtime_core web font support."
    dependsOn(extractFreetypeSource)
    outputs.dir(webFreetypeBuildDir)
    doFirst {
        webFreetypeBuildDir.get().asFile.mkdirs()
        webFreetypeGeneratedResources.get().asFile.mkdirs()
    }
    commandLine(executableCommand("emcmake") + listOf(
        "cmake",
        "-S", webFreetypeCmakeDir.asFile.absolutePath,
        "-B", webFreetypeBuildDir.get().asFile.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DLIBFDX_FREETYPE_SOURCE_DIR=${freetypeSourceDir.get().asFile.absolutePath}",
        "-DLIBFDX_RUNTIME_CORE_NATIVE_DIR=${runtimeCoreNativeDir.asFile.absolutePath}",
        "-DLIBFDX_WEB_OUTPUT_DIR=${webFreetypeGeneratedResources.get().asFile.absolutePath}"
    ))
}

val buildWebFreetypeEmscripten = tasks.register<Exec>("build_web_freetype_emscripten") {
    group = "libfdx native"
    description = "Builds the Emscripten JS/WASM FreeType runtime used by runtime_core web font support."
    dependsOn(configureWebFreetypeEmscripten)
    outputs.file(webFreetypeGeneratedResources.map { it.file("fdx.js") })
    outputs.file(webFreetypeGeneratedResources.map { it.file("fdx.wasm") })
    doFirst {
        deleteUnexpectedWebRuntimeArtifacts(webFreetypeGeneratedResources.get().asFile)
    }
    commandLine(executableCommand("cmake") + listOf(
        "--build", webFreetypeBuildDir.get().asFile.absolutePath,
        "--config", "Release"
    ))
}

sourceSets {
    named("main") {
        resources.srcDir(desktopRuntimeCoreGeneratedResources)
        resources.srcDir(webFreetypeGeneratedResources)
    }
}

tasks.named<ProcessResources>("processResources") {
    doFirst {
        deleteUnexpectedWebRuntimeArtifacts(webFreetypeGeneratedResources.get().asFile)
        deleteUnexpectedWebRuntimeArtifacts(destinationDir)
    }
}
