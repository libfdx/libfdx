
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.time.Instant
import java.util.Locale
import java.util.Properties

plugins {
    id("java")
}


base {
    archivesName.set("benchmark_desktop")
}

val glRuntimeClasspath = configurations.create("glRuntimeClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val wgpuJniRuntimeClasspath = configurations.create("wgpuJniRuntimeClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val vulkanRuntimeClasspath = configurations.create("vulkanRuntimeClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val wgpuFfmRuntimeClasspath = configurations.create("wgpuFfmRuntimeClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

val windowsX64 = System.getProperty("os.name", "").lowercase().startsWith("windows") &&
        System.getProperty("os.arch", "").lowercase() in setOf("amd64", "x86_64")

dependencies {
    implementation(project(":benchmark:core"))
    implementation(project(":libfdx:backends:desktop"))
    implementation(project(":libfdx:extensions:graphics:d3d12:core"))
    implementation(project(":libfdx:extensions:graphics:wgpu:core"))

    glRuntimeClasspath(project(":libfdx:extensions:graphics:gl:platform:desktop"))
    vulkanRuntimeClasspath(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
    wgpuJniRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_jni"))
    wgpuFfmRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
}

val benchmarkMainClass = "io.github.libfdx.benchmark.desktop.DesktopBenchmarkLauncher"
val spriteBatchResultFiles = mutableListOf<File>()

fun JavaExec.configureSpriteBatchStressRun(
    descriptionText: String,
    resultName: String,
    graphics: String,
    graphicsLabel: String,
    providerClasspath: FileCollection
) {
    group = "benchmark"
    description = descriptionText
    classpath = sourceSets["main"].runtimeClasspath + providerClasspath
    mainClass.set(benchmarkMainClass)
    workingDir = rootProject.projectDir
    val resultFile = layout.buildDirectory.file("benchmark-results/sprite-batch-stress/$resultName.properties").get().asFile
    spriteBatchResultFiles.add(resultFile)
    outputs.file(resultFile)
    outputs.upToDateWhen { false }
    doFirst {
        resultFile.parentFile.mkdirs()
        if (resultFile.exists()) {
            resultFile.delete()
        }
    }
    systemProperty("libfdx.benchmark.name", "sprite_batch_stress")
    systemProperty("libfdx.benchmark.graphics", graphics)
    systemProperty("libfdx.benchmark.graphicsLabel", graphicsLabel)
    systemProperty("libfdx.benchmark.result", resultFile.absolutePath)
    systemProperty("libfdx.benchmark.seconds", System.getProperty("libfdx.benchmark.seconds", "8"))
    systemProperty("libfdx.benchmark.warmupSeconds", System.getProperty("libfdx.benchmark.warmupSeconds", "2"))
    System.getProperty("libfdx.benchmark.sprites")?.let { systemProperty("libfdx.benchmark.sprites", it) }
    listOf("device", "driver", "revision").forEach { field ->
        systemProperty("libfdx.benchmark.$field", System.getProperty("libfdx.benchmark.$field", "unspecified"))
    }
    systemProperty("libfdx.benchmark.visible", System.getProperty("libfdx.benchmark.visible", "true"))
    systemProperty("libfdx.benchmark.vsync", "false")
    systemProperty("libfdx.benchmark.foregroundFps", "0")
    jvmArgs("-Dorg.lwjgl.system.stackSize=1024")
    if (JavaVersion.current().majorVersion.toInt() >= 22) {
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}

fun JavaExec.useJava25Launcher() {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

fun JavaExec.useLwjglJniOnJava25() {
    useJava25Launcher()
    // LWJGL 3.4.x stores its FFM backend in Java 25 multi-release entries.
    jvmArgs("-Djdk.util.jar.enableMultiRelease=false")
}

val benchmarkSpriteBatchStressGlFfm = tasks.register<JavaExec>("benchmark_sprite_batch_stress_gl_ffm") {
    configureSpriteBatchStressRun(
        "Runs SpriteBatch stress benchmark with GL on Java 25 using LWJGL FFM.",
        "gl-ffm",
        "gl",
        "GL LWJGL FFM",
        glRuntimeClasspath
    )
    useJava25Launcher()
}

val benchmarkSpriteBatchStressGlJni = tasks.register<JavaExec>("benchmark_sprite_batch_stress_gl_jni") {
    configureSpriteBatchStressRun(
        "Runs SpriteBatch stress benchmark with GL on Java 25 using LWJGL JNI.",
        "gl-jni",
        "gl",
        "GL LWJGL JNI",
        glRuntimeClasspath
    )
    useLwjglJniOnJava25()
}

val spritePackingMode = providers.systemProperty("libfdx.benchmark.spritePacking")
val spritePackingSource = layout.projectDirectory.dir("src/main/cpp")
val spritePackingBuild = layout.buildDirectory.dir("sprite-packing-native")
val spritePackingLibrary = spritePackingBuild.map { it.file(System.mapLibraryName("libfdx_sprite_packing")) }
val configureSpritePackingNative = tasks.register<Exec>("configureSpritePackingNative") {
    val jdk = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) }
    val cmakeProject = spritePackingSource.file("CMakeLists.txt")
    inputs.file(cmakeProject)
    inputs.property("javaHome", jdk.map { it.metadata.installationPath.asFile.absolutePath })
    outputs.file(spritePackingBuild.map { it.file("CMakeCache.txt") })
    doFirst {
        val buildDir = spritePackingBuild.get().asFile.absolutePath
        environment("JAVA_HOME", jdk.get().metadata.installationPath.asFile.absolutePath)
        commandLine("cmake", "-S", spritePackingSource.asFile.absolutePath, "-B", buildDir,
            "-DCMAKE_BUILD_TYPE=Release", "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY_RELEASE=$buildDir",
            "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY_RELEASE=$buildDir")
    }
}
val buildSpritePackingNative = tasks.register<Exec>("buildSpritePackingNative") {
    dependsOn(configureSpritePackingNative)
    inputs.dir(spritePackingSource)
    inputs.file(spritePackingBuild.map { it.file("CMakeCache.txt") })
    outputs.file(spritePackingLibrary)
    doFirst {
        commandLine("cmake", "--build", spritePackingBuild.get().asFile.absolutePath, "--config", "Release")
    }
}

val nativeSpriteLibrary = spritePackingBuild.map { it.file(System.mapLibraryName("libfdx_sprite_native")) }
val buildNativeSpriteBenchmark = tasks.register<Exec>("buildNativeSpriteBenchmark") {
    dependsOn(configureSpritePackingNative)
    inputs.dir(spritePackingSource)
    inputs.file(spritePackingBuild.map { it.file("CMakeCache.txt") })
    outputs.file(nativeSpriteLibrary)
    doFirst {
        require(windowsX64) { "The C++ JNI sprite benchmark requires Windows x64 and MSVC" }
        commandLine("cmake", "--build", spritePackingBuild.get().asFile.absolutePath, "--config", "Release",
            "--target", "libfdx_sprite_native")
    }
}

tasks.register<JavaExec>("benchmark_sprite_batch_libfdx_cpp_jni") {
    group = "benchmark"
    description = "Runs the libFDX sprite workload using a C++ renderer and a thin Java JNI entry point."
    dependsOn("classes", buildNativeSpriteBenchmark)
    mainClass.set("io.github.libfdx.benchmark.desktop.NativeSpriteBenchmarkLauncher")
    classpath = sourceSets["main"].output
    workingDir = rootProject.projectDir
    useJava25Launcher()
    systemProperty("libfdx.benchmark.nativeSpriteLibrary", nativeSpriteLibrary.get().asFile.absolutePath)
    val result = System.getProperty("libfdx.benchmark.result")
        ?: layout.buildDirectory.file("benchmark-results/libfdx-cpu-cpp-jni.properties").get().asFile.absolutePath
    args("--result=$result", "--sprites=" + System.getProperty("libfdx.benchmark.sprites", "8191"),
        "--seconds=" + System.getProperty("libfdx.benchmark.seconds", "20"),
        "--warmupSeconds=" + System.getProperty("libfdx.benchmark.warmupSeconds", "5"),
        "--frameProfile=" + System.getProperty("libfdx.benchmark.frameProfile", "false"))
    System.getProperty("libfdx.benchmark.capture")?.let { args("--capture=$it") }
    System.getProperty("libfdx.benchmark.cpuDiagnostic")?.let { args("--cpuDiagnostic=$it") }
}

tasks.register<JavaExec>("benchmark_sprite_batch_libfdx_gl_jni") {
    group = "benchmark"
    description = "Runs the libFDX CPU sprite benchmark through libFDX OpenGL on the JVM."
    classpath = sourceSets["main"].runtimeClasspath + glRuntimeClasspath
    mainClass.set(benchmarkMainClass)
    workingDir = rootProject.projectDir
    useLwjglJniOnJava25()
    systemProperty("libfdx.benchmark.name", "sprite_batch_libfdx")
    systemProperty("libfdx.benchmark.graphics", "gl")
    systemProperty("libfdx.benchmark.seconds", System.getProperty("libfdx.benchmark.seconds", "20"))
    systemProperty("libfdx.benchmark.warmupSeconds", "5")
    systemProperty("libfdx.benchmark.visible", "true")
    systemProperty("libfdx.benchmark.vsync", "false")
    systemProperty("libfdx.benchmark.foregroundFps", "0")
    val packingMode = spritePackingMode.orNull
    require(packingMode == null || packingMode == "java" || packingMode == "cpp") { "spritePacking must be java or cpp" }
    if (packingMode != null) systemProperty("libfdx.benchmark.spritePacking", packingMode)
    if (packingMode == "cpp") {
        dependsOn(buildSpritePackingNative)
        systemProperty("libfdx.benchmark.spritePackingLibrary", spritePackingLibrary.get().asFile.absolutePath)
    }
    val resultName = if (packingMode == null) "libfdx-cpu-jvm" else "libfdx-cpu-jvm-$packingMode"
    systemProperty("libfdx.benchmark.result", System.getProperty("libfdx.benchmark.result")
        ?: layout.buildDirectory.file("benchmark-results/$resultName.properties").get().asFile.absolutePath)
    System.getProperty("libfdx.benchmark.frameProfile")?.let { systemProperty("libfdx.benchmark.frameProfile", it) }
    System.getProperty("libfdx.benchmark.sprites")?.let { systemProperty("libfdx.benchmark.sprites", it) }
    System.getProperty("libfdx.benchmark.capture")?.let { systemProperty("libfdx.benchmark.capture", it) }
}

val benchmarkSpriteBatchStressWgpuJni = tasks.register<JavaExec>("benchmark_sprite_batch_stress_wgpu_jni") {
    configureSpriteBatchStressRun(
        "Runs SpriteBatch stress benchmark with WGPU JNI.",
        "wgpu-jni",
        "wgpu",
        "WGPU JNI",
        wgpuJniRuntimeClasspath
    )
}

val benchmarkSpriteBatchStressWgpuFfm = tasks.register<JavaExec>("benchmark_sprite_batch_stress_wgpu_ffm") {
    configureSpriteBatchStressRun(
        "Runs SpriteBatch stress benchmark with WGPU FFM.",
        "wgpu-ffm",
        "wgpu",
        "WGPU FFM",
        wgpuFfmRuntimeClasspath
    )
    useJava25Launcher()
}

val benchmarkSpriteBatchStressVulkanFfm = tasks.register<JavaExec>("benchmark_sprite_batch_stress_vulkan_ffm") {
    configureSpriteBatchStressRun(
        "Runs SpriteBatch stress benchmark with Vulkan on Java 25 using LWJGL FFM.",
        "vulkan-ffm",
        "vulkan",
        "Vulkan LWJGL FFM",
        vulkanRuntimeClasspath
    )
    useJava25Launcher()
}

val benchmarkSpriteBatchStressVulkanJni = tasks.register<JavaExec>("benchmark_sprite_batch_stress_vulkan_jni") {
    configureSpriteBatchStressRun(
        "Runs SpriteBatch stress benchmark with Vulkan on Java 25 using LWJGL JNI.",
        "vulkan-jni",
        "vulkan",
        "Vulkan LWJGL JNI",
        vulkanRuntimeClasspath
    )
    useLwjglJniOnJava25()
}

val benchmarkSpriteBatchStressD3D12 = if (windowsX64) {
    tasks.register<JavaExec>("benchmark_sprite_batch_stress_d3d12") {
        configureSpriteBatchStressRun(
            "Runs SpriteBatch stress benchmark with Direct3D 12 through Java 25 FFM.",
            "d3d12",
            "d3d12",
            "Direct3D 12",
            files()
        )
        useJava25Launcher()
    }
} else {
    null
}

benchmarkSpriteBatchStressGlJni.configure {
    mustRunAfter(benchmarkSpriteBatchStressGlFfm)
}

benchmarkSpriteBatchStressWgpuJni.configure {
    mustRunAfter(benchmarkSpriteBatchStressGlJni)
}

benchmarkSpriteBatchStressWgpuFfm.configure {
    mustRunAfter(benchmarkSpriteBatchStressWgpuJni)
}

benchmarkSpriteBatchStressVulkanFfm.configure {
    mustRunAfter(benchmarkSpriteBatchStressWgpuFfm)
}

benchmarkSpriteBatchStressVulkanJni.configure {
    mustRunAfter(benchmarkSpriteBatchStressVulkanFfm)
}

benchmarkSpriteBatchStressD3D12?.configure {
    mustRunAfter(benchmarkSpriteBatchStressVulkanJni)
}

val spriteBatchStressTasks = listOf(
    benchmarkSpriteBatchStressGlFfm,
    benchmarkSpriteBatchStressGlJni,
    benchmarkSpriteBatchStressWgpuJni,
    benchmarkSpriteBatchStressWgpuFfm,
    benchmarkSpriteBatchStressVulkanFfm,
    benchmarkSpriteBatchStressVulkanJni
) + listOfNotNull(benchmarkSpriteBatchStressD3D12)

val generateSpriteBatchStressReport = tasks.register("generate_sprite_batch_stress_report") {
    group = "benchmark"
    description = "Generates a Markdown report for the desktop SpriteBatch stress benchmark."
    dependsOn(spriteBatchStressTasks)
    val reportFile = rootProject.layout.buildDirectory.file("reports/benchmark/desktop-sprite-batch-stress.md").get().asFile
    outputs.file(reportFile)
    outputs.upToDateWhen { false }
    doLast {
        val results = spriteBatchResultFiles.map { file ->
            if (!file.isFile) {
                throw GradleException("Missing benchmark result: $file")
            }
            val properties = Properties()
            file.inputStream().use { properties.load(it) }
            if (properties.getProperty("completed") != "true"
                    || properties.getProperty("measuredIntervals", "0").toLong() == 0L) {
                throw GradleException("Benchmark needs a completed run with measured intervals after warm-up: $file")
            }
            properties
        }.sortedByDescending { it.getProperty("measuredFrameFps", "0").toDouble() }

        val fastest = results.firstOrNull()
        val fastestFps = fastest?.getProperty("measuredFrameFps", "0")?.toDouble() ?: 0.0
        val slowest = results.lastOrNull()
        reportFile.parentFile.mkdirs()
        reportFile.writeText(buildString {
            appendLine("# Desktop SpriteBatch Stress Benchmark")
            appendLine()
            appendLine("- Generated: ${Instant.now()}")
            appendLine("- Report: `${reportFile.relativeTo(rootProject.projectDir).invariantSeparatorsPath}`")
            appendLine("- Benchmark: rotating/scaling sprites; count is recorded per run")
            appendLine("- Sprite: 32x32 from `benchmark/assets/fdx.png`")
            appendLine("- Runtime: vSync off, foreground frame limiter off; visibility is recorded per run")
            appendLine("- Backend tuning: Vulkan and Direct3D 12 use 3 frames in flight; WGPU skips per-frame instance event polling")
            appendLine("- Ranking uses complete render-start intervals after warm-up, including presentation/pacing; CPU timings exclude presentation")
            appendLine("- Percentiles are histogram upper bounds (less than 3.125% bucket error); hitches exceed 50 ms")
            appendLine()
            appendLine("| Rank | Graphics Option | Provider | Java | Intervals | Measured (s) | FPS | Sprite Draws/s | Relative |")
            appendLine("| ---: | --- | --- | --- | ---: | ---: | ---: | ---: | ---: |")
            results.forEachIndexed { index, result ->
                val fps = result.getProperty("measuredFrameFps", "0").toDouble()
                val relative = if (fastestFps > 0.0) fps / fastestFps * 100.0 else 0.0
                val spriteDrawsPerSecond = result.getProperty("measuredSpriteDrawsPerSecond", "0")
                appendLine("| ${index + 1} | ${result.getProperty("label")} | ${result.getProperty("graphicsProvider")} | ${result.getProperty("javaVersion")} | ${result.getProperty("measuredIntervals")} | ${result.getProperty("measuredSeconds")} | ${format(fps)} | $spriteDrawsPerSecond | ${format(relative)}% |")
            }
            appendLine()
            appendLine("| Graphics Option | Sprites | Visible | Resolution | Warm-up (s) | p50 (ms) | p95 (ms) | p99 (ms) | Worst (ms) | Hitches | CPU mean (ms) |")
            appendLine("| --- | ---: | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
            results.forEach { result ->
                appendLine("| ${result.getProperty("label")} | ${result.getProperty("sprites")} | ${result.getProperty("visible")} | ${result.getProperty("framebufferWidth")}x${result.getProperty("framebufferHeight")} | ${result.getProperty("warmupSeconds")} | ${result.getProperty("frameTimeP50Millis")} | ${result.getProperty("frameTimeP95Millis")} | ${result.getProperty("frameTimeP99Millis")} | ${result.getProperty("frameTimeWorstMillis")} | ${result.getProperty("frameTimeHitches")} | ${result.getProperty("cpuRenderMeanMillis")} |")
            }
            appendLine()
            if (fastest != null && slowest != null) {
                val slowestFps = slowest.getProperty("measuredFrameFps", "0").toDouble()
                val slowestRelative = if (fastestFps > 0.0) slowestFps / fastestFps * 100.0 else 0.0
                appendLine("Fastest option: **${fastest.getProperty("label")}** at ${format(fastestFps)} FPS.")
                appendLine()
                appendLine("Slowest option: **${slowest.getProperty("label")}** at ${format(slowestFps)} FPS (${format(slowestRelative)}% of fastest).")
                appendLine()
            }
            appendLine("Raw result files:")
            spriteBatchResultFiles.forEach { file ->
                appendLine("- `${file.relativeTo(rootProject.projectDir).invariantSeparatorsPath}`")
            }
        })
        println("Benchmark report written to ${reportFile.absolutePath}")
    }
}

tasks.register("benchmark_sprite_batch_stress") {
    group = "benchmark"
    description = "Runs the desktop SpriteBatch stress benchmark and generates a Markdown report."
    dependsOn(generateSpriteBatchStressReport)
}

tasks.register("benchmark_desktop") {
    group = "benchmark"
    description = "Runs the full desktop benchmark suite and generates Markdown reports."
    dependsOn(generateSpriteBatchStressReport)
}

fun format(value: Double): String {
    return String.format(Locale.ROOT, "%.2f", value)
}
