import io.github.libfdx.build.LibExt

import org.gradle.api.tasks.TaskProvider
import java.time.Instant
import java.util.Locale
import java.util.Properties

plugins {
    id("java")
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.benchmark"


val nativeTargetFileName = "libfdx-benchmark-desktop-native"
val nativeOpenConsole = providers.gradleProperty("libfdx.desktopNative.openConsole")
        .map { value -> value.toBooleanStrictOrNull() ?: value.toBoolean() }
        .orElse(true)

base {
    archivesName.set("benchmark_desktop_native")
}

dependencies {
    implementation(project(":benchmark:core"))
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_desktop_native:${LibExt.publishedLibfdxVersion}")

        runtimeOnly("${LibExt.fdxGroup}:gl_desktop_native:${LibExt.publishedLibfdxVersion}")
        runtimeOnly("${LibExt.fdxGroup}:vulkan_desktop_native:${LibExt.publishedLibfdxVersion}")
    } else {
        implementation(project(":libfdx:backends:desktop_native"))

        runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop_native"))
        runtimeOnly(project(":libfdx:extensions:graphics:vulkan:platform:desktop_native"))
    }
}

libfdx {
    desktopNative {
        mainClass.set("io.github.libfdx.benchmark.desktopnative.DesktopNativeBenchmarkLauncher")
        targetFileName.set(nativeTargetFileName)
        buildType.set("Release")
        minHeapSize.set(64)
        maxHeapSize.set(1024)
    }
}

fun isWindowsHost(): Boolean {
    return System.getProperty("os.name", "").lowercase().contains("win")
}

fun windowsPowerShellStartCommand(executable: File, args: List<String>, workingDirectory: File): List<String> {
    val argumentList = args.joinToString(", ") { powershellSingleQuoted(it) }
    val script = "${'$'}p = Start-Process -FilePath ${powershellSingleQuoted(executable.absolutePath)} " +
            "-ArgumentList @($argumentList) " +
            "-WorkingDirectory ${powershellSingleQuoted(workingDirectory.absolutePath)} " +
            "-Wait -PassThru; exit ${'$'}p.ExitCode"
    return listOf("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script)
}

fun powershellSingleQuoted(value: String): String {
    return "'" + value.replace("'", "''") + "'"
}

data class NativeBenchmarkGraphicsOption(
        val taskId: String,
        val argument: String,
        val displayName: String,
        val reportName: String,
        val tuning: String)

val nativeBenchmarkGraphicsOptions = listOf(
        NativeBenchmarkGraphicsOption(
                "gl",
                "gl",
                "GL desktop_native",
                "GL",
                "GL uses the desktop_native GL provider and GLEW native resource module"),
        NativeBenchmarkGraphicsOption(
                "vulkan",
                "vulkan",
                "Vulkan desktop_native",
                "Vulkan",
                "Vulkan uses 3 frames in flight"))

fun registerNativeBenchmarkGraphicsMode(
        graphicsOption: NativeBenchmarkGraphicsOption,
        modeSuffix: String,
        buildType: String,
        nativeBuildTask: String): TaskProvider<*> {
    val nativeResultFile = layout.buildDirectory
            .file("benchmark-results/sprite-batch-stress/native-${graphicsOption.taskId}-$modeSuffix.properties")
            .get().asFile

    val runSpriteBatchStressNative = tasks.register<Exec>(
            "run_sprite_batch_stress_native_${graphicsOption.taskId}_$modeSuffix") {
        group = "benchmark"
        description = "Runs the raw SpriteBatch stress benchmark process with ${graphicsOption.displayName} $buildType."
        dependsOn(nativeBuildTask)
        workingDir = rootProject.projectDir
        outputs.file(nativeResultFile)
        outputs.upToDateWhen { false }
        doFirst {
            nativeResultFile.parentFile.mkdirs()
            if (nativeResultFile.exists()) {
                nativeResultFile.delete()
            }
            val executable = layout.buildDirectory.file(
                    "dist/desktop-native/c/release/$nativeTargetFileName" + "_$modeSuffix"
                            + if (isWindowsHost()) ".exe" else "").get().asFile
            if (!executable.isFile) {
                throw GradleException("Native executable was not built: ${executable.absolutePath}")
            }
            val args = listOf(
                    "--benchmark=sprite_batch_stress",
                    "--graphics=" + graphicsOption.argument,
                    "--seconds=" + System.getProperty("libfdx.benchmark.seconds", "8"),
                    "--result=" + nativeResultFile.absolutePath,
                    "--visible=" + System.getProperty("libfdx.benchmark.visible", "true"),
                    "--vsync=false",
                    "--foregroundFps=" + System.getProperty("libfdx.benchmark.foregroundFps", "0"))
            if (isWindowsHost() && nativeOpenConsole.get()) {
                commandLine(windowsPowerShellStartCommand(executable, args, rootProject.projectDir))
            } else {
                commandLine(listOf(executable.absolutePath) + args)
            }
        }
    }

    val generateSpriteBatchStressNativeReport = tasks.register(
            "generate_sprite_batch_stress_native_${graphicsOption.taskId}_report_$modeSuffix") {
        group = "benchmark"
        description = "Generates a Markdown report for the ${graphicsOption.displayName} SpriteBatch stress benchmark $buildType."
        dependsOn(runSpriteBatchStressNative)
        val reportFile = rootProject.layout.buildDirectory
                .file("reports/benchmark/desktop-native-${graphicsOption.taskId}-sprite-batch-stress-$modeSuffix.md")
                .get().asFile
        outputs.file(reportFile)
        outputs.upToDateWhen { false }
        doLast {
            if (!nativeResultFile.isFile) {
                throw GradleException("Missing benchmark result: $nativeResultFile")
            }
            val result = Properties()
            nativeResultFile.inputStream().use { result.load(it) }
            val visible = result.getProperty("visible", System.getProperty("libfdx.benchmark.visible", "true"))
            val vSync = result.getProperty("vSync", System.getProperty("libfdx.benchmark.vsync", "false"))
            val foregroundFps = result.getProperty("foregroundFps", System.getProperty("libfdx.benchmark.foregroundFps", "0"))
            val foregroundLimiter = if (foregroundFps == "0") "off" else foregroundFps
            reportFile.parentFile.mkdirs()
            reportFile.writeText(buildString {
                appendLine("# Desktop Native ${graphicsOption.reportName} SpriteBatch Stress Benchmark - $buildType")
                appendLine()
                appendLine("- Generated: ${Instant.now()}")
                appendLine("- Report: `${reportFile.relativeTo(rootProject.projectDir).invariantSeparatorsPath}`")
                appendLine("- Benchmark: 8191 rotating/scaling sprites")
                appendLine("- Sprite: 32x32 from `fdx.png`")
                appendLine("- Runtime: visible=$visible, vSync=$vSync, foregroundFps=$foregroundLimiter")
                appendLine("- Graphics: `${graphicsOption.displayName}`")
                appendLine("- Build type: `$buildType`")
                appendLine("- Backend tuning: ${graphicsOption.tuning}")
                appendLine()
                appendLine("| Graphics Option | Provider | Java | Frames | Elapsed (s) | Avg FPS | Sprite Draws/s |")
                appendLine("| --- | --- | --- | ---: | ---: | ---: | ---: |")
                appendLine("| ${result.getProperty("label")} | ${result.getProperty("graphicsProvider")} | "
                        + "${result.getProperty("javaVersion")} | ${result.getProperty("frames")} | "
                        + "${result.getProperty("elapsedSeconds")} | "
                        + "${format(result.getProperty("averageFrameFps", "0").toDouble())} | "
                        + "${result.getProperty("averageSpriteDrawsPerSecond")} |")
                appendLine()
                appendLine("Raw result files:")
                appendLine("- `${nativeResultFile.relativeTo(rootProject.projectDir).invariantSeparatorsPath}`")
            })
            println("Benchmark report written to ${reportFile.absolutePath}")
        }
    }

    tasks.register("benchmark_sprite_batch_stress_native_${graphicsOption.taskId}_$modeSuffix") {
        group = "benchmark"
        description = "Runs the ${graphicsOption.displayName} SpriteBatch stress benchmark $buildType and generates a Markdown report."
        dependsOn(generateSpriteBatchStressNativeReport)
    }

    tasks.register("benchmark_desktop_native_${graphicsOption.taskId}_$modeSuffix") {
        group = "benchmark"
        description = "Runs the ${graphicsOption.displayName} benchmark suite $buildType and generates Markdown reports."
        dependsOn(generateSpriteBatchStressNativeReport)
    }

    return generateSpriteBatchStressNativeReport
}

fun registerNativeBenchmarkMode(modeSuffix: String, buildType: String, nativeBuildTask: String) {
    val reportTasks = nativeBenchmarkGraphicsOptions.map { graphicsOption ->
        registerNativeBenchmarkGraphicsMode(graphicsOption, modeSuffix, buildType, nativeBuildTask)
    }

    tasks.register("benchmark_desktop_native_$modeSuffix") {
        group = "benchmark"
        description = "Runs the full desktop_native $buildType benchmark suite across GL and Vulkan and generates Markdown reports."
        dependsOn(reportTasks)
    }
}

registerNativeBenchmarkMode("debug", "Debug", "libfdx_desktop_native_build_debug")
registerNativeBenchmarkMode("release", "Release", "libfdx_desktop_native_build_release")

fun format(value: Double): String {
    return String.format(Locale.ROOT, "%.2f", value)
}
