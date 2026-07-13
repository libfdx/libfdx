import io.github.libfdx.build.LibExt

import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.time.Instant
import java.util.Locale
import java.util.Properties

plugins {
    id("java")
}

group = "${LibExt.fdxGroup}.benchmark"

base {
    archivesName.set("benchmark_desktop")
}

val glRuntimeClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val wgpuJniRuntimeClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val vulkanRuntimeClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val wgpuFfmRuntimeClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

dependencies {
    implementation(project(":benchmark:core"))
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_desktop:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:wgpu_core:${LibExt.fdxSnapshotVersion}")

        glRuntimeClasspath("${LibExt.fdxGroup}:gl_desktop:${LibExt.fdxSnapshotVersion}")
        vulkanRuntimeClasspath("${LibExt.fdxGroup}:vulkan_desktop:${LibExt.fdxSnapshotVersion}")
        wgpuJniRuntimeClasspath("${LibExt.fdxGroup}:wgpu_desktop_jni:${LibExt.fdxSnapshotVersion}")
        wgpuFfmRuntimeClasspath("${LibExt.fdxGroup}:wgpu_desktop_ffm:${LibExt.fdxSnapshotVersion}")
    } else {
        implementation(project(":libfdx:backends:desktop"))
        implementation(project(":libfdx:extensions:graphics:wgpu:core"))

        glRuntimeClasspath(project(":libfdx:extensions:graphics:gl:platform:desktop"))
        vulkanRuntimeClasspath(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
        wgpuJniRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_jni"))
        wgpuFfmRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
    }
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
    systemProperty("libfdx.benchmark.visible", System.getProperty("libfdx.benchmark.visible", "true"))
    systemProperty("libfdx.benchmark.vsync", "false")
    systemProperty("libfdx.benchmark.foregroundFps", "0")
    jvmArgs("-Dorg.lwjgl.system.stackSize=1048576")
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

val spriteBatchStressTasks = listOf(
    benchmarkSpriteBatchStressGlFfm,
    benchmarkSpriteBatchStressGlJni,
    benchmarkSpriteBatchStressWgpuJni,
    benchmarkSpriteBatchStressWgpuFfm,
    benchmarkSpriteBatchStressVulkanFfm,
    benchmarkSpriteBatchStressVulkanJni
)

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
            properties
        }.sortedByDescending { it.getProperty("averageFrameFps", "0").toDouble() }

        val fastest = results.firstOrNull()
        val fastestFps = fastest?.getProperty("averageFrameFps", "0")?.toDouble() ?: 0.0
        val slowest = results.lastOrNull()
        reportFile.parentFile.mkdirs()
        reportFile.writeText(buildString {
            appendLine("# Desktop SpriteBatch Stress Benchmark")
            appendLine()
            appendLine("- Generated: ${Instant.now()}")
            appendLine("- Report: `${reportFile.relativeTo(rootProject.projectDir).invariantSeparatorsPath}`")
            appendLine("- Benchmark: 8191 rotating/scaling sprites")
            appendLine("- Sprite: 32x32 from `benchmark/assets/fdx.png`")
            appendLine("- Runtime: visible window, vSync off, foreground frame limiter off")
            appendLine("- Backend tuning: Vulkan uses 3 frames in flight; WGPU skips per-frame instance event polling")
            appendLine()
            appendLine("| Rank | Graphics Option | Provider | Java | Frames | Elapsed (s) | Avg FPS | Sprite Draws/s | Relative |")
            appendLine("| ---: | --- | --- | --- | ---: | ---: | ---: | ---: | ---: |")
            results.forEachIndexed { index, result ->
                val fps = result.getProperty("averageFrameFps", "0").toDouble()
                val relative = if (fastestFps > 0.0) fps / fastestFps * 100.0 else 0.0
                val spriteDrawsPerSecond = result.getProperty("averageSpriteDrawsPerSecond", "0")
                appendLine("| ${index + 1} | ${result.getProperty("label")} | ${result.getProperty("graphicsProvider")} | ${result.getProperty("javaVersion")} | ${result.getProperty("frames")} | ${result.getProperty("elapsedSeconds")} | ${format(fps)} | $spriteDrawsPerSecond | ${format(relative)}% |")
            }
            appendLine()
            if (fastest != null && slowest != null) {
                val slowestFps = slowest.getProperty("averageFrameFps", "0").toDouble()
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
