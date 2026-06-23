import io.github.libfdx.build.LibExt
import java.io.File
import java.net.URI
import java.security.MessageDigest
import org.gradle.api.Task
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider

plugins {
    base
}

group = "${LibExt.fdxGroup}.runtime.fdx"

description = "Internal runtime fdx native build orchestration."

val runtimeFdxSharedNativeRoot = rootProject.layout.projectDirectory.dir("libfdx/runtime/fdx/platform/shared/src/main/cpp")
val runtimeFdxNativeDir = runtimeFdxSharedNativeRoot.dir("runtime_fdx")
val runtimeFdxShaderCompilerDir = runtimeFdxSharedNativeRoot.dir("shader_compiler")
val runtimeFdxDesktopCmakeDir = runtimeFdxSharedNativeRoot.dir("runtime_fdx")
val runtimeFdxAndroidCmakeDir = rootProject.layout.projectDirectory.dir("libfdx/runtime/fdx/platform/android/src/main/cpp")
val runtimeFdxWebCmakeDir = rootProject.layout.projectDirectory.dir("libfdx/runtime/fdx/platform/web/src/main/cpp")

val runtimeFdxPrebuiltVersion = "0.1.1"

val runtimeFdxPrebuiltRoot = layout.buildDirectory.dir("prebuilt/fdx-natives")

val runtimeFdxDesktopResources = layout.buildDirectory.dir("generated/resources/runtimeFdxDesktop")
val runtimeFdxWebResources = layout.buildDirectory.dir("generated/resources/runtimeFdxWeb")
val runtimeFdxAndroidJniLibs = layout.buildDirectory.dir("generated/jniLibs/runtimeFdxAndroid")

val runtimeFdxPrebuiltBaseUrl = providers
    .gradleProperty("libfdx.runtimeFdx.nativeDepsBaseUrl")

fun runtimeFdxPrebuiltReleaseBaseUrl(): String {
    return runtimeFdxPrebuiltBaseUrl.orNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.trimEnd('/')
        ?: "https://github.com/libfdx/fdx-natives/releases/download/v$runtimeFdxPrebuiltVersion"
}

val runtimeFdxPrebuiltManifestUrl = providers
    .gradleProperty("libfdx.runtimeFdx.prebuiltManifestUrl")
    .orElse(providers.provider { "${runtimeFdxPrebuiltReleaseBaseUrl()}/fdx-natives-manifest.json" })

val runtimeFdxAndroidNdkVersion = providers
    .gradleProperty("libfdx.runtimeFdx.androidNdkVersion")
    .orElse("27.0.12077973")

val runtimeFdxAndroidMinSdk = providers
    .gradleProperty("libfdx.runtimeFdx.androidMinSdk")
    .orElse("29")

val runtimeFdxAndroidCmakeVersion = providers
    .gradleProperty("libfdx.runtimeFdx.androidCmakeVersion")
    .orElse("3.22.1")

val nativeBuildParallelism = providers.gradleProperty("libfdx.nativeBuildParallelism")
    .map(String::toInt)
    .orElse(Runtime.getRuntime().availableProcessors().coerceAtMost(8).coerceAtLeast(1))

fun runtimeFdxHostOs(): String {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("win") -> "windows"
        osName.contains("mac") || osName.contains("darwin") -> "macos"
        osName.contains("linux") -> "linux"
        else -> throw GradleException("Unsupported host OS for runtime fdx native build: ${System.getProperty("os.name")}")
    }
}

fun runtimeFdxHostArch(): String {
    val archName = System.getProperty("os.arch").lowercase()
    return when (archName) {
        "amd64", "x86_64" -> "x64"
        "aarch64", "arm64" -> "arm64"
        else -> throw GradleException("Unsupported host architecture for runtime fdx native build: ${System.getProperty("os.arch")}")
    }
}

fun runtimeFdxMacosClassifier(): String {
    return when (runtimeFdxHostArch()) {
        "arm64" -> "macos-arm64"
        else -> "macos-x64"
    }
}

fun runtimeFdxPrebuiltClassifier(platform: String): String {
    return when (platform) {
        "windows" -> "windows-x64-msvc"
        "linux" -> "linux-x64-gcc"
        "macos" -> when (runtimeFdxMacosClassifier()) {
            "macos-arm64" -> "macos-arm64-appleclang"
            else -> "macos-x64-appleclang"
        }
        "web" -> "web-emscripten"
        else -> throw GradleException("Unsupported runtime fdx prebuilt platform '$platform'.")
    }
}

fun runtimeFdxAndroidPrebuiltClassifier(abi: String): String {
    return when (abi) {
        "arm64-v8a" -> "android-arm64-v8a"
        "armeabi-v7a" -> "android-armeabi-v7a"
        "x86" -> "android-x86"
        "x86_64" -> "android-x86_64"
        else -> throw GradleException("Unsupported runtime fdx Android ABI '$abi'.")
    }
}

fun runtimeFdxCmakeBuildDir(platform: String) = layout.buildDirectory.dir("cmake/runtimeFdx/$platform")

fun runtimeFdxDesktopOutput(platform: String, classifier: String, fileName: String): Provider<RegularFile> {
    return runtimeFdxDesktopResources.map { it.file("libfdx-native/desktop/$classifier/$fileName") }
}

fun runtimeFdxWebOutput(fileName: String): Provider<RegularFile> {
    return runtimeFdxWebResources.map { it.file(fileName) }
}

fun runtimeFdxAndroidOutput(abi: String): Provider<RegularFile> {
    return runtimeFdxAndroidJniLibs.map { it.file("$abi/libfdx.so") }
}

fun executableCommand(name: String): String {
    val windows = System.getProperty("os.name").lowercase().contains("win")
    if (!windows) {
        return name
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
    val candidates = listOf("$name.exe", "$name.bat", "$name.cmd", name)
    for (directory in searchDirectories) {
        for (candidate in candidates) {
            val file = File(directory, candidate)
            if (file.isFile) {
                return file.absolutePath
            }
        }
    }
    return name
}

fun platformExecutableName(name: String): String {
    return if (System.getProperty("os.name").lowercase().contains("win")) "$name.exe" else name
}

fun requireRuntimeFdxHost(targetOs: String, taskName: String) {
    if (runtimeFdxHostOs() != targetOs) {
        throw GradleException("$taskName must run on $targetOs. Current host is ${runtimeFdxHostOs()}.")
    }
}

fun localPropertiesValue(key: String): String? {
    val file = rootProject.layout.projectDirectory.file("local.properties").asFile
    if (!file.isFile) return null
    return file.readLines()
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull {
            val index = it.indexOf("=")
            if (index <= 0) null else it.substring(0, index).trim() to it.substring(index + 1).trim()
        }
        .firstOrNull { it.first == key }
        ?.second
}

fun androidSdkDir(): File {
    val candidates = listOfNotNull(
        providers.gradleProperty("android.sdk.dir").orNull,
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
        localPropertiesValue("sdk.dir"),
    ).map { File(it) }
    return candidates.firstOrNull { it.isDirectory }
        ?: throw GradleException("Android SDK not found. Set ANDROID_HOME, ANDROID_SDK_ROOT, android.sdk.dir, or local.properties sdk.dir.")
}

fun androidNdkDir(sdkDir: File): File {
    val ndkDir = File(sdkDir, "ndk/${runtimeFdxAndroidNdkVersion.get()}")
    if (!ndkDir.isDirectory) {
        throw GradleException("Android NDK ${runtimeFdxAndroidNdkVersion.get()} was not found at $ndkDir.")
    }
    return ndkDir
}

fun androidCmakeCommand(@Suppress("UNUSED_PARAMETER") sdkDir: File): String {
    return executableCommand("cmake")
}

fun androidNinjaCommand(sdkDir: File): String? {
    val pathNinja = File(executableCommand("ninja"))
    if (pathNinja.isFile) {
        return pathNinja.absolutePath
    }
    val cmakeDir = File(sdkDir, "cmake/${runtimeFdxAndroidCmakeVersion.get()}/bin")
    return File(cmakeDir, platformExecutableName("ninja")).takeIf(File::isFile)?.absolutePath
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

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun downloadFile(url: String, destination: File) {
    destination.parentFile.mkdirs()
    URI(url).toURL().openStream().use { input ->
        destination.outputStream().use { output ->
            input.copyTo(output)
        }
    }
}

val resolveRuntimeFdxPrebuiltManifest = tasks.register("resolve_runtime_fdx_prebuilt_manifest") {
    group = "runtime fdx native"
    description = "Downloads the fdx-natives release manifest."
    val manifest = runtimeFdxPrebuiltRoot.map { it.file("fdx-natives-manifest.json") }
    outputs.file(manifest)

    doLast {
        val destination = manifest.get().asFile
        if (!destination.isFile) {
            downloadFile(runtimeFdxPrebuiltManifestUrl.get(), destination)
        }
    }
}

fun runtimeFdxPrebuiltPackageName(classifier: String): String {
    return "fdx-natives-$classifier.zip"
}

fun runtimeFdxPrebuiltZipUrl(packageName: String): Provider<String> {
    return providers.provider {
        "${runtimeFdxPrebuiltReleaseBaseUrl()}/$packageName"
    }
}

fun runtimeFdxPrebuiltManifestSha256(packageName: String): String {
    val manifest = runtimeFdxPrebuiltRoot.get().file("fdx-natives-manifest.json").asFile
    if (!manifest.isFile) {
        throw GradleException("Missing fdx-natives manifest ${manifest.absolutePath}")
    }
    val text = manifest.readText()
    val pattern = Regex(
        "\\{\\s*\"name\"\\s*:\\s*\"${Regex.escape(packageName)}\"\\s*,\\s*\"sha256\"\\s*:\\s*\"([0-9a-fA-F]{64})\"",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )
    return pattern.find(text)?.groupValues?.get(1)
        ?: throw GradleException("Manifest ${manifest.absolutePath} does not contain package $packageName")
}

fun registerResolveRuntimeFdxPrebuilt(classifier: String): TaskProvider<Sync> {
    val taskName = "resolve_runtime_fdx_prebuilt_${classifier.replace('-', '_')}"
    val packageName = runtimeFdxPrebuiltPackageName(classifier)
    val zipFile = runtimeFdxPrebuiltRoot.map { it.file("downloads/$packageName") }
    val extractDir = runtimeFdxPrebuiltRoot.map { it.dir(classifier) }
    val downloadTask = tasks.register("download_runtime_fdx_prebuilt_${classifier.replace('-', '_')}") {
        group = "runtime fdx native"
        description = "Downloads and verifies the fdx-natives prebuilt package for $classifier."
        dependsOn(resolveRuntimeFdxPrebuiltManifest)
        outputs.file(zipFile)

        doLast {
            val destination = zipFile.get().asFile
            val expectedSha256 = runtimeFdxPrebuiltManifestSha256(packageName)
            if (!destination.isFile || sha256(destination) != expectedSha256) {
                downloadFile(runtimeFdxPrebuiltZipUrl(packageName).get(), destination)
            }
            val actualSha256 = sha256(destination)
            if (actualSha256 != expectedSha256) {
                destination.delete()
                throw GradleException(
                    "fdx-natives package checksum mismatch for $packageName. Expected $expectedSha256 but got $actualSha256."
                )
            }
        }
    }

    return tasks.register<Sync>(taskName) {
        group = "runtime fdx native"
        description = "Extracts the fdx-natives prebuilt package for $classifier."
        dependsOn(downloadTask)

        from(zipTree(zipFile))
        into(extractDir)
    }
}

val runtimeFdxPrebuiltClassifiers = listOf(
    "windows-x64-msvc",
    "linux-x64-gcc",
    "macos-x64-appleclang",
    "macos-arm64-appleclang",
    "android-arm64-v8a",
    "android-armeabi-v7a",
    "android-x86",
    "android-x86_64",
    "web-emscripten",
)

val runtimeFdxPrebuiltTasks = runtimeFdxPrebuiltClassifiers
    .associateWith { classifier -> registerResolveRuntimeFdxPrebuilt(classifier) }

fun runtimeFdxPrebuiltDependency(classifier: String): TaskProvider<Sync> {
    return runtimeFdxPrebuiltTasks[classifier]
        ?: throw GradleException("Unsupported fdx-natives classifier '$classifier'.")
}

fun runtimeFdxNativeInputsDependency(prebuiltClassifier: String): TaskProvider<Sync> {
    return runtimeFdxPrebuiltDependency(prebuiltClassifier)
}

fun runtimeFdxNativeDependencyArgs(prebuiltClassifier: String): List<String> {
    return listOf(
        "-DLIBFDX_NATIVE_DEPS_DIR=${runtimeFdxPrebuiltRoot.get().asFile.absolutePath.replace('\\', '/')}/${prebuiltClassifier}",
    )
}

fun runtimeFdxDesktopCmakeArgs(
    buildDir: File,
    outputDir: File,
    prebuiltClassifier: String,
): List<String> {
    return listOf(
        "-S", runtimeFdxDesktopCmakeDir.asFile.absolutePath,
        "-B", buildDir.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DLIBFDX_DESKTOP_OUTPUT_DIR=${outputDir.absolutePath.replace('\\', '/')}",
        "-DLIBFDX_JAVA_HOME=${System.getProperty("java.home").replace('\\', '/')}",
        "-DLIBFDX_ENABLE_SHADER_COMPILER=ON",
        "-DLIBFDX_SHADERC_SOURCE_DIR=${runtimeFdxShaderCompilerDir.asFile.absolutePath.replace('\\', '/')}",
    ) + runtimeFdxNativeDependencyArgs(prebuiltClassifier)
}

fun registerRuntimeFdxDesktopNativeTasks(
    platform: String,
    classifier: String,
    libraryFileName: String,
    expectedHost: String,
): TaskProvider<Exec> {
    val prebuiltClassifier = runtimeFdxPrebuiltClassifier(platform)
    val configureTask = tasks.register<Exec>("configure_runtime_fdx_${platform}_native") {
        group = "runtime fdx native"
        description = "Configures runtime fdx $platform native build."
        dependsOn(runtimeFdxNativeInputsDependency(prebuiltClassifier))
        val cmakeBuildDir = runtimeFdxCmakeBuildDir(platform)
        val outputDir = runtimeFdxDesktopResources.map { it.dir("libfdx-native/desktop/$classifier") }
        inputs.dir(runtimeFdxNativeDir)
        inputs.dir(runtimeFdxShaderCompilerDir)
        outputs.dir(cmakeBuildDir)
        doFirst {
            requireRuntimeFdxHost(expectedHost, name)
            commandLine(executableCommand("cmake"))
            args(runtimeFdxDesktopCmakeArgs(cmakeBuildDir.get().asFile, outputDir.get().asFile, prebuiltClassifier))
        }
    }

    return tasks.register<Exec>("build_runtime_fdx_${platform}_native") {
        group = "runtime fdx native"
        description = "Builds runtime fdx $platform native library."
        dependsOn(configureTask)
        val cmakeBuildDir = runtimeFdxCmakeBuildDir(platform)
        val outputFile = runtimeFdxDesktopOutput(platform, classifier, libraryFileName)
        outputs.file(outputFile)
        commandLine(
            executableCommand("cmake"),
            "--build",
            cmakeBuildDir.get().asFile.absolutePath,
            "--config",
            "Release",
            "--parallel",
            nativeBuildParallelism.get().toString(),
        )
    }
}

val buildRuntimeFdxWindowsNative = registerRuntimeFdxDesktopNativeTasks(
    platform = "windows",
    classifier = "windows-x64",
    libraryFileName = "fdx.dll",
    expectedHost = "windows",
)

val buildRuntimeFdxLinuxNative = registerRuntimeFdxDesktopNativeTasks(
    platform = "linux",
    classifier = "linux-x64",
    libraryFileName = "libfdx.so",
    expectedHost = "linux",
)

val buildRuntimeFdxMacosNative = registerRuntimeFdxDesktopNativeTasks(
    platform = "macos",
    classifier = runtimeFdxMacosClassifier(),
    libraryFileName = "libfdx.dylib",
    expectedHost = "macos",
)

fun registerPrebuiltAlias(name: String, target: TaskProvider<out Task>) {
    tasks.register(name) {
        group = "runtime fdx native"
        description = "Builds runtime fdx native artifacts using fdx-natives prebuilt dependencies."
        dependsOn(target)
    }
}

registerPrebuiltAlias("build_runtime_fdx_windows_native_prebuilt", buildRuntimeFdxWindowsNative)
registerPrebuiltAlias("build_runtime_fdx_linux_native_prebuilt", buildRuntimeFdxLinuxNative)
registerPrebuiltAlias("build_runtime_fdx_macos_native_prebuilt", buildRuntimeFdxMacosNative)

val configureRuntimeFdxWebNative = tasks.register<Exec>("configure_runtime_fdx_web_native") {
    group = "runtime fdx native"
    description = "Configures runtime fdx web native build."
    val prebuiltClassifier = runtimeFdxPrebuiltClassifier("web")
    dependsOn(runtimeFdxNativeInputsDependency(prebuiltClassifier))
    inputs.dir(runtimeFdxNativeDir)
    inputs.dir(runtimeFdxWebCmakeDir)
    outputs.dir(runtimeFdxCmakeBuildDir("web"))
    doFirst {
        useEmsdkBundledPython()
    }
    commandLine(
        buildList {
            add(executableCommand("emcmake"))
            add("cmake")
            add("-S")
            add(runtimeFdxWebCmakeDir.asFile.absolutePath)
            add("-B")
            add(runtimeFdxCmakeBuildDir("web").get().asFile.absolutePath)
            add("-DCMAKE_BUILD_TYPE=Release")
            add("-DLIBFDX_RUNTIME_FDX_NATIVE_DIR=${runtimeFdxNativeDir.asFile.absolutePath.replace('\\', '/')}")
            add("-DLIBFDX_WEB_OUTPUT_DIR=${runtimeFdxWebResources.get().asFile.absolutePath.replace('\\', '/')}")
            add("-DLIBFDX_ENABLE_SHADER_COMPILER=ON")
            add("-DLIBFDX_SHADERC_SOURCE_DIR=${runtimeFdxShaderCompilerDir.asFile.absolutePath.replace('\\', '/')}")
            emsdkBundledPython()?.let { add("-DPython3_EXECUTABLE=${it.absolutePath.replace('\\', '/')}") }
        }
    )
    args(runtimeFdxNativeDependencyArgs(prebuiltClassifier))
}

val buildRuntimeFdxWebNative = tasks.register<Exec>("build_runtime_fdx_web_native") {
    group = "runtime fdx native"
    description = "Builds runtime fdx web native artifacts."
    dependsOn(configureRuntimeFdxWebNative)
    outputs.file(runtimeFdxWebOutput("fdx.js"))
    outputs.file(runtimeFdxWebOutput("fdx.wasm"))
    doFirst {
        useEmsdkBundledPython()
    }
    commandLine(
        executableCommand("cmake"),
        "--build",
        runtimeFdxCmakeBuildDir("web").get().asFile.absolutePath,
        "--config",
        "Release",
        "--parallel",
        nativeBuildParallelism.get().toString(),
    )
}

registerPrebuiltAlias("build_runtime_fdx_web_native_prebuilt", buildRuntimeFdxWebNative)

fun registerRuntimeFdxAndroidNativeTasks(abi: String): TaskProvider<Exec> {
    val safeAbi = abi.replace('-', '_')
    val prebuiltClassifier = runtimeFdxAndroidPrebuiltClassifier(abi)
    val buildDir = runtimeFdxCmakeBuildDir("android/$safeAbi")
    val outputDir = runtimeFdxAndroidJniLibs.map { it.dir(abi) }

    val configureTask = tasks.register<Exec>("configure_runtime_fdx_android_${safeAbi}_native") {
        group = "runtime fdx native"
        description = "Configures runtime fdx Android native build for $abi."
        dependsOn(runtimeFdxNativeInputsDependency(prebuiltClassifier))
        inputs.dir(runtimeFdxNativeDir)
        inputs.dir(runtimeFdxAndroidCmakeDir)
        outputs.dir(buildDir)

        doFirst {
            val sdkDir = androidSdkDir()
            val ndkDir = androidNdkDir(sdkDir)
            commandLine(
                androidCmakeCommand(sdkDir),
                "-G",
                "Ninja",
                "-S",
                runtimeFdxAndroidCmakeDir.asFile.absolutePath,
                "-B",
                buildDir.get().asFile.absolutePath,
                "-DCMAKE_BUILD_TYPE=Release",
                "-DCMAKE_TOOLCHAIN_FILE=${File(ndkDir, "build/cmake/android.toolchain.cmake").absolutePath.replace('\\', '/')}",
                "-DANDROID_ABI=$abi",
                "-DANDROID_PLATFORM=android-${runtimeFdxAndroidMinSdk.get()}",
                "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=${outputDir.get().asFile.absolutePath.replace('\\', '/')}",
                "-DLIBFDX_RUNTIME_FDX_NATIVE_DIR=${runtimeFdxNativeDir.asFile.absolutePath.replace('\\', '/')}",
                "-DLIBFDX_ENABLE_SHADER_COMPILER=ON",
                "-DLIBFDX_SHADERC_SOURCE_DIR=${runtimeFdxShaderCompilerDir.asFile.absolutePath.replace('\\', '/')}",
            )
            androidNinjaCommand(sdkDir)?.let { args("-DCMAKE_MAKE_PROGRAM=${it.replace('\\', '/')}") }
            args(runtimeFdxNativeDependencyArgs(prebuiltClassifier))
        }
    }

    return tasks.register<Exec>("build_runtime_fdx_android_${safeAbi}_native") {
        group = "runtime fdx native"
        description = "Builds runtime fdx Android native library for $abi."
        dependsOn(configureTask)
        outputs.file(runtimeFdxAndroidOutput(abi))
        doFirst {
            val sdkDir = androidSdkDir()
            commandLine(
                androidCmakeCommand(sdkDir),
                "--build",
                buildDir.get().asFile.absolutePath,
                "--config",
                "Release",
                "--parallel",
                nativeBuildParallelism.get().toString(),
            )
        }
    }
}

val runtimeFdxAndroidAbis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
val buildRuntimeFdxAndroidAbiTasks = runtimeFdxAndroidAbis.map { registerRuntimeFdxAndroidNativeTasks(it) }

val buildRuntimeFdxAndroidNative = tasks.register("build_runtime_fdx_android_native") {
    group = "runtime fdx native"
    description = "Builds runtime fdx Android native libraries for all supported ABIs."
    dependsOn(buildRuntimeFdxAndroidAbiTasks)
}

registerPrebuiltAlias("build_runtime_fdx_android_native_prebuilt", buildRuntimeFdxAndroidNative)

tasks.register("build_runtime_fdx_native") {
    group = "runtime fdx native"
    description = "Builds runtime fdx native artifacts for the current host, Android, and web using fdx-natives prebuilt dependencies."
    dependsOn(
        when (runtimeFdxHostOs()) {
            "windows" -> buildRuntimeFdxWindowsNative
            "linux" -> buildRuntimeFdxLinuxNative
            "macos" -> buildRuntimeFdxMacosNative
            else -> throw GradleException("Unsupported host OS ${runtimeFdxHostOs()}.")
        },
        buildRuntimeFdxWebNative,
        buildRuntimeFdxAndroidNative,
    )
}

tasks.register("build_runtime_fdx_native_prebuilt") {
    group = "runtime fdx native"
    description = "Builds runtime fdx native artifacts using fdx-natives prebuilt dependencies."
    dependsOn("build_runtime_fdx_native")
}
