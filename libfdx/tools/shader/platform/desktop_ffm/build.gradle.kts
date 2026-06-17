plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("shader_compiler_desktop_ffm")
}

dependencies {
    api(project(":libfdx:tools:shader:core"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val generatedShadercResources = layout.buildDirectory.dir("generated/resources/shaderc")
val requiredDesktopShadercResources = listOf(
    "libfdx/shader/native/desktop/windows-x86_64/fdx_shaderc.dll",
    "libfdx/shader/native/desktop/linux-x86_64/libfdx_shaderc.so",
    "libfdx/shader/native/desktop/macos-x86_64/libfdx_shaderc.dylib",
    "libfdx/shader/native/desktop/macos-arm64/libfdx_shaderc.dylib"
)

fun hostClassifier(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val osName = when {
        os.contains("windows") -> "windows"
        os.contains("linux") -> "linux"
        os.contains("mac") || os.contains("darwin") -> "macos"
        else -> os.replace(Regex("[^a-z0-9]+"), "")
    }
    val archName = when {
        arch == "amd64" || arch == "x86_64" -> "x86_64"
        arch == "aarch64" || arch == "arm64" -> "arm64"
        else -> arch.replace(Regex("[^a-z0-9]+"), "")
    }
    return "$osName-$archName"
}

fun nativeLibraryName(): String {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("windows") -> "fdx_shaderc.dll"
        os.contains("mac") || os.contains("darwin") -> "libfdx_shaderc.dylib"
        else -> "libfdx_shaderc.so"
    }
}

tasks.register<Sync>("generate_shaderc_desktop_native") {
    group = "libfdx native"
    description = "Builds and stages the host shader compiler native library for desktop FFM runtime use."
    dependsOn(":libfdx:tools:shader:core:build_shaderc_host")
    from(project(":libfdx:tools:shader:core").layout.buildDirectory.dir("native/shaderc/host")) {
        include(nativeLibraryName())
        into("libfdx/shader/native/desktop/${hostClassifier()}")
    }
    into(generatedShadercResources)
}

val validateShadercDesktopNativeResources = tasks.register("validate_shaderc_desktop_native_resources") {
    group = "libfdx native"
    description = "Validates staged desktop shader compiler native resources before packaging."
    mustRunAfter("generate_shaderc_desktop_native")
    inputs.files(requiredDesktopShadercResources.map { path ->
        generatedShadercResources.map { dir -> dir.file(path) }
    })
    doLast {
        val root = generatedShadercResources.get().asFile
        val missing = requiredDesktopShadercResources
            .map { root.resolve(it) }
            .filterNot { it.isFile }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing generated desktop shader compiler resources:\n" +
                        missing.joinToString(separator = "\n") { " - ${it.absolutePath}" }
            )
        }
    }
}

sourceSets {
    main {
        resources.srcDir(generatedShadercResources)
    }
}

tasks.named<ProcessResources>("processResources") {
    mustRunAfter("generate_shaderc_desktop_native")
}

tasks.named("jar") {
    dependsOn(validateShadercDesktopNativeResources)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    val nativeLibrary = project(":libfdx:tools:shader:core").layout.buildDirectory
        .file("native/shaderc/host/${nativeLibraryName()}")
    systemProperty("libfdx.shaderc.nativeLibrary", nativeLibrary.get().asFile.absolutePath)
}
