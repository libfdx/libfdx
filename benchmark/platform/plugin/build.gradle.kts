
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.attributes.java.TargetJvmVersion
import java.time.Instant
import java.time.Duration
import java.util.Locale
import java.util.Properties

plugins {
    id("java")
    id("io.github.libfdx")
}


base {
    archivesName.set("benchmark_plugin")
}

val cppSourceSet = sourceSets.create("cpp")
tasks.named<JavaCompile>(cppSourceSet.compileJavaTaskName) {
    sourceCompatibility = "25"
    targetCompatibility = "25"
}
listOf(cppSourceSet.compileClasspathConfigurationName, cppSourceSet.runtimeClasspathConfigurationName).forEach {
    configurations[it].attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
}
dependencies {
    implementation(project(":benchmark:platform:desktop_native"))
    add(cppSourceSet.implementationConfigurationName,
        project(path = ":benchmark:platform:desktop_native", configuration = "cppRuntimeElements"))
}

val nativeTargetFileName = "libfdx-benchmark-desktop-c"
val cppTargetFileName = "libfdx-benchmark-desktop-cpp"
val cppOutputDir = rootProject.layout.buildDirectory.dir("benchmark-cpp")

libfdx {
    desktopC {
        mainClass.set("io.github.libfdx.benchmark.desktopc.DesktopCBenchmarkLauncher")
        targetFileName.set(nativeTargetFileName)
        buildType.set("Release")
        obfuscated.set(false)
        minHeapSize.set(64)
        maxHeapSize.set(1024)
    }
    desktopCPP {
        sourceSet.set(cppSourceSet.name)
        mainClass.set("io.github.libfdx.benchmark.desktopcpp.DesktopCppBenchmarkLauncher")
        targetFileName.set(cppTargetFileName)
        // Keep generated object paths within jNative's portable path budget.
        outputDir.set(cppOutputDir)
    }
}

fun isWindowsHost(): Boolean {
    return System.getProperty("os.name", "").lowercase().contains("win")
}

fun nativeExecutable(runtime: NativeBenchmarkRuntime, modeSuffix: String): File {
    val suffix = if (modeSuffix.equals("release", ignoreCase = true)) "release" else "debug"
    if (runtime.taskId == "desktop_graal") {
        return project(":benchmark:platform:desktop_native").layout.buildDirectory
            .file("native/nativeGraalCompile/libfdx-benchmark-desktop-graal" +
                if (isWindowsHost()) ".exe" else "").get().asFile
    }
    if (runtime.taskId == "desktop_cpp") {
        return cppOutputDir.get().file("$suffix/native/$suffix/$cppTargetFileName" +
            if (isWindowsHost()) ".exe" else "").asFile
    }
    return layout.buildDirectory.file(
        "dist/desktop-c/c/release/${nativeTargetFileName}_$suffix" + if (isWindowsHost()) ".exe" else ""
    ).get().asFile
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
    val tuning: String
)

data class NativeBenchmarkRuntime(val taskId: String, val pathId: String, val displayName: String, val consoleProperty: String)

val nativeBenchmarkRuntimes = listOf(
    NativeBenchmarkRuntime("desktop_c", "desktop-c", "Desktop C (TeaVM)", "libfdx.desktopC.openConsole"),
    NativeBenchmarkRuntime("desktop_cpp", "desktop-cpp", "Desktop C++ (jNative)", "libfdx.desktopCPP.openConsole"),
    NativeBenchmarkRuntime("desktop_graal", "desktop-graal", "Desktop GraalVM Native Image", "libfdx.desktopGraal.openConsole")
)
val nativeBenchmarkRunTasks = mutableListOf<TaskProvider<Exec>>()

fun registerLibfdxCpuRun(runtimeId: String, buildTask: String): TaskProvider<Exec> {
    val runtime = nativeBenchmarkRuntimes.first { it.taskId == runtimeId }
    return tasks.register<Exec>("benchmark_libfdx_${runtimeId}_gl_release") {
        group = "benchmark"
        description = "Runs the libFDX CPU sprite benchmark through libFDX OpenGL with ${runtime.displayName}."
        dependsOn(buildTask)
        workingDir = rootProject.projectDir
        timeout.set(Duration.ofSeconds(
            System.getProperty("libfdx.benchmark.seconds", "20").toDouble().toLong() + 60))
        val result = layout.buildDirectory.file("benchmark-results/libfdx-cpu/$runtimeId.properties")
        outputs.file(result)
        outputs.upToDateWhen { false }
        doFirst {
            val executable = if (runtimeId == "desktop_graal") project(":benchmark:platform:desktop_native")
                .layout.buildDirectory.file("native/cpuComparison/libfdx-benchmark-desktop-graal.exe").get().asFile
                else nativeExecutable(runtime, "release")
            val resultFile = result.get().asFile
            resultFile.parentFile.mkdirs()
            if (resultFile.exists() && !resultFile.delete()) throw GradleException("Cannot replace $resultFile")
            commandLine(executable.absolutePath, "--benchmark=sprite_batch_libfdx", "--graphics=gl",
                "--seconds=" + System.getProperty("libfdx.benchmark.seconds", "20"),
                "--warmupSeconds=" + System.getProperty("libfdx.benchmark.warmupSeconds", "5"),
                "--sprites=" + System.getProperty("libfdx.benchmark.sprites", "8191"),
                "--vsync=false", "--foregroundFps=0", "--visible=true", "--result=${resultFile.absolutePath}")
        }
        doLast {
            val values = Properties().also { result.get().asFile.inputStream().use(it::load) }
            val sprites = values.getProperty("sprites").toInt()
            if (values.getProperty("completed") != "true" || values.getProperty("benchmark") != "sprite_batch_libfdx" ||
                values.getProperty("uploadedVertexBytes").toLong() != sprites.toLong() * 80 ||
                values.getProperty("renderCalls").toInt() != (sprites + 8190) / 8191 ||
                values.getProperty("measuredIntervals").toLong() == 0L) {
                throw GradleException("CPU benchmark did not complete: ${result.get().asFile}")
            }
            println("${runtime.displayName}: ${values.getProperty("measuredFrameFps")} FPS; " +
                "${values.getProperty("renderCalls")} draws; ${values.getProperty("uploadedVertexBytes")} vertex bytes/frame")
        }
    }
}

val libfdxCpuC = registerLibfdxCpuRun("desktop_c", "libfdx_desktop_c_build_release")
val libfdxCpuCpp = registerLibfdxCpuRun("desktop_cpp", "libfdx_desktop_cpp_build_release")
val libfdxCpuGraal = registerLibfdxCpuRun("desktop_graal",
    ":benchmark:platform:desktop_native:benchmark_desktop_graal_cpu_build_release")
libfdxCpuC.configure {
    mustRunAfter("libfdx_desktop_cpp_build_release", ":benchmark:platform:desktop_native:benchmark_desktop_graal_cpu_build_release")
}
libfdxCpuCpp.configure {
    mustRunAfter(libfdxCpuC, ":benchmark:platform:desktop_native:benchmark_desktop_graal_cpu_build_release")
}
libfdxCpuGraal.configure { mustRunAfter(libfdxCpuC, libfdxCpuCpp) }
tasks.register("benchmark_libfdx_native_gl_release") {
    group = "benchmark"
    description = "Builds all three runtimes, then compares the libFDX CPU sprite benchmark sequentially."
    dependsOn(libfdxCpuC, libfdxCpuCpp, libfdxCpuGraal)
}

fun nativeBenchmarkGraphicsOptions(runtime: NativeBenchmarkRuntime) = listOf(
    NativeBenchmarkGraphicsOption(
        "gl",
        "gl",
        "GL ${runtime.taskId}",
        "GL",
        if (runtime.taskId == "desktop_graal") "GL uses the desktop GL provider and LWJGL JNI"
        else "GL uses the ${runtime.taskId} GL provider and GLEW native resource module"
    ),
    NativeBenchmarkGraphicsOption(
        "vulkan",
        "vulkan",
        "Vulkan ${runtime.taskId}",
        "Vulkan",
        "Vulkan uses 3 frames in flight"
    )
)

fun registerNativeBenchmarkGraphicsMode(
    runtime: NativeBenchmarkRuntime,
    graphicsOption: NativeBenchmarkGraphicsOption,
    modeSuffix: String,
    buildType: String,
    nativeBuildTask: String
): TaskProvider<*> {
    val nativeResultFile = layout.buildDirectory
        .file("benchmark-results/sprite-batch-stress/${runtime.pathId}-${graphicsOption.taskId}-$modeSuffix.properties")
        .get().asFile

    val earlierNativeRunTasks = nativeBenchmarkRunTasks.toList()
    val runSpriteBatchStressNative = tasks.register<Exec>(
        "run_sprite_batch_stress_${runtime.taskId}_${graphicsOption.taskId}_$modeSuffix"
    ) {
        group = "benchmark"
        description = "Runs the raw SpriteBatch stress benchmark process with ${graphicsOption.displayName} $buildType."
        dependsOn(nativeBuildTask)
        // Order against every earlier run so partial aggregate selections also serialize.
        mustRunAfter(earlierNativeRunTasks)
        workingDir = rootProject.projectDir
        outputs.file(nativeResultFile)
        outputs.upToDateWhen { false }
        doFirst {
            nativeResultFile.parentFile.mkdirs()
            if (nativeResultFile.exists()) {
                nativeResultFile.delete()
            }
            val executable = nativeExecutable(runtime, modeSuffix)
            if (!executable.isFile) {
                throw GradleException("Native executable was not built: ${executable.absolutePath}")
            }
            val args = mutableListOf(
                "--benchmark=sprite_batch_stress",
                "--graphics=" + graphicsOption.argument,
                "--seconds=" + System.getProperty("libfdx.benchmark.seconds", "8"),
                "--warmupSeconds=" + System.getProperty("libfdx.benchmark.warmupSeconds", "2"),
                "--device=" + System.getProperty("libfdx.benchmark.device", "unspecified"),
                "--driver=" + System.getProperty("libfdx.benchmark.driver", "unspecified"),
                "--revision=" + System.getProperty("libfdx.benchmark.revision", "unspecified"),
                "--result=" + nativeResultFile.absolutePath,
                "--visible=" + System.getProperty("libfdx.benchmark.visible", "true"),
                "--vsync=false",
                "--foregroundFps=" + System.getProperty("libfdx.benchmark.foregroundFps", "0")
            )
            System.getProperty("libfdx.benchmark.sprites")?.let { args.add("--sprites=$it") }
            val openConsole = providers.gradleProperty(runtime.consoleProperty)
                .map { it.toBooleanStrictOrNull() ?: it.toBoolean() }.orElse(false).get()
            if (isWindowsHost() && openConsole) {
                commandLine(windowsPowerShellStartCommand(executable, args, rootProject.projectDir))
            } else {
                commandLine(listOf(executable.absolutePath) + args)
            }
        }
    }
    nativeBenchmarkRunTasks.add(runSpriteBatchStressNative)

    val generateSpriteBatchStressNativeReport = tasks.register(
        "generate_sprite_batch_stress_${runtime.taskId}_${graphicsOption.taskId}_report_$modeSuffix"
    ) {
        group = "benchmark"
        description = "Generates a Markdown report for the ${graphicsOption.displayName} SpriteBatch stress benchmark $buildType."
        dependsOn(runSpriteBatchStressNative)
        val reportFile = rootProject.layout.buildDirectory
            .file("reports/benchmark/${runtime.pathId}-${graphicsOption.taskId}-sprite-batch-stress-$modeSuffix.md")
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
            val measured = result.getProperty("measuredFrameFps") != null
            val fpsLabel = if (measured) "Measured FPS" else "Avg FPS"
            val throughput = result.getProperty("measuredSpriteDrawsPerSecond",
                result.getProperty("averageSpriteDrawsPerSecond", "0"))
            reportFile.parentFile.mkdirs()
            reportFile.writeText(buildString {
                appendLine("# ${runtime.displayName} ${graphicsOption.reportName} SpriteBatch Stress Benchmark - $buildType")
                appendLine()
                appendLine("- Generated: ${Instant.now()}")
                appendLine("- Report: `${reportFile.relativeTo(rootProject.projectDir).invariantSeparatorsPath}`")
                appendLine("- Benchmark: ${result.getProperty("sprites")} rotating/scaling sprites")
                appendLine("- Sprite: 32x32 from `benchmark/assets/fdx.png`")
                appendLine("- Runtime: visible=$visible, vSync=$vSync, foregroundFps=$foregroundLimiter")
                appendLine("- Graphics: `${graphicsOption.displayName}`")
                appendLine("- Build type: `$buildType`")
                appendLine("- Backend tuning: ${graphicsOption.tuning}")
                if (measured) appendLine("- Measurement: excludes ${result.getProperty("warmupSeconds", "0")} s warm-up; " +
                    "${result.getProperty("measuredIntervals")} intervals in ${result.getProperty("measuredSeconds")} s")
                appendLine()
                appendLine("| Graphics Option | Provider | Java | Frames | Elapsed (s) | $fpsLabel | Sprite Draws/s |")
                appendLine("| --- | --- | --- | ---: | ---: | ---: | ---: |")
                appendLine("| ${result.getProperty("label")} | ${result.getProperty("graphicsProvider")} | " +
                    "${result.getProperty("javaVersion")} | ${result.getProperty("frames")} | " +
                    "${result.getProperty("elapsedSeconds")} | " +
                    "${format(result.getProperty("measuredFrameFps", result.getProperty("averageFrameFps", "0")).toDouble())} | " +
                    "$throughput |")
                appendLine()
                appendLine("Raw result files:")
                appendLine("- `${nativeResultFile.relativeTo(rootProject.projectDir).invariantSeparatorsPath}`")
            })
            println("Benchmark report written to ${reportFile.absolutePath}")
        }
    }

    tasks.register("benchmark_sprite_batch_stress_${runtime.taskId}_${graphicsOption.taskId}_$modeSuffix") {
        group = "benchmark"
        description = "Runs the ${graphicsOption.displayName} SpriteBatch stress benchmark $buildType and generates a Markdown report."
        dependsOn(generateSpriteBatchStressNativeReport)
    }

    tasks.register("benchmark_${runtime.taskId}_${graphicsOption.taskId}_$modeSuffix") {
        group = "benchmark"
        description = "Runs the ${graphicsOption.displayName} benchmark suite $buildType and generates Markdown reports."
        dependsOn(generateSpriteBatchStressNativeReport)
    }

    return generateSpriteBatchStressNativeReport
}

fun registerNativeBenchmarkMode(runtime: NativeBenchmarkRuntime, modeSuffix: String, buildType: String) {
    val reportTasks = nativeBenchmarkGraphicsOptions(runtime).map { graphicsOption ->
        registerNativeBenchmarkGraphicsMode(runtime, graphicsOption, modeSuffix, buildType,
            nativeBuildTask = if (runtime.taskId == "desktop_graal")
                ":benchmark:platform:desktop_native:benchmark_desktop_graal_build_release"
            else "libfdx_${runtime.taskId}_build_$modeSuffix")
    }

    tasks.register("benchmark_${runtime.taskId}_$modeSuffix") {
        group = "benchmark"
        description = "Runs the full ${runtime.displayName} $buildType benchmark suite across GL and Vulkan and generates Markdown reports."
        dependsOn(reportTasks)
    }
}

nativeBenchmarkRuntimes.forEach { runtime ->
    if (runtime.taskId != "desktop_graal") registerNativeBenchmarkMode(runtime, "debug", "Debug")
    registerNativeBenchmarkMode(runtime, "release", "Release")
}
// Finish every selected runtime build before any measured native run starts, including --parallel builds.
val nativeBuildTasks = listOf("debug", "release").flatMap { mode ->
    listOf("desktop_c", "desktop_cpp").map { "libfdx_${it}_build_$mode" }
} + listOf("nativeGraalInstrumentedCompile", "benchmark_desktop_graal_train_gl",
    "benchmark_desktop_graal_train_vulkan", "nativeGraalCompile").map { ":benchmark:platform:desktop_native:$it" }
nativeBenchmarkRunTasks.forEach { run ->
    run.configure { mustRunAfter(nativeBuildTasks) }
}
listOf("debug", "release").forEach { mode ->
    tasks.register("benchmark_desktop_native_$mode") {
        group = "benchmark"
        description = "Runs the desktop native $mode benchmark suites and generates Markdown reports."
        dependsOn(nativeBenchmarkRuntimes.filter { mode == "release" || it.taskId != "desktop_graal" }
            .map { "benchmark_${it.taskId}_$mode" })
    }
}

fun format(value: Double): String {
    return String.format(Locale.ROOT, "%.2f", value)
}
