import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import java.util.Properties

plugins {
    id("java")
}


base {
    archivesName.set("benchmark_desktop_native")
}

// Each native runtime consumes only its source set and dependencies.
val cppSourceSet = sourceSets.create("cpp")
val graalSourceSet = sourceSets.create("graal")
tasks.named<JavaCompile>(graalSourceSet.compileJavaTaskName) {
    sourceCompatibility = "25"
    targetCompatibility = "25"
}
listOf(graalSourceSet.compileClasspathConfigurationName, graalSourceSet.runtimeClasspathConfigurationName).forEach {
    configurations[it].attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
}
tasks.named<JavaCompile>(cppSourceSet.compileJavaTaskName) {
    sourceCompatibility = "25"
    targetCompatibility = "25"
}
listOf(cppSourceSet.compileClasspathConfigurationName, cppSourceSet.runtimeClasspathConfigurationName).forEach {
    configurations[it].attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
}
val cppJar = tasks.register<Jar>("cppJar") {
    archiveClassifier.set("cpp")
    from(cppSourceSet.output)
}
configurations.create("cppRuntimeElements") {
    isCanBeConsumed = true
    isCanBeResolved = false
    extendsFrom(configurations[cppSourceSet.implementationConfigurationName], configurations[cppSourceSet.runtimeOnlyConfigurationName])
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
    // Keep ordinary project dependencies on the TeaVM main artifact.
    outgoing.capability("${project.group}:${project.name}-cpp:${project.version}")
    outgoing.artifact(cppJar)
}

dependencies {
    add(graalSourceSet.implementationConfigurationName, project(":benchmark:core"))
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        add(graalSourceSet.implementationConfigurationName, "${libs.versions.libfdxGroup.get()}:backend_desktop:${libs.versions.libfdxSnapshot.get()}")
        add(graalSourceSet.runtimeOnlyConfigurationName, "${libs.versions.libfdxGroup.get()}:gl_desktop:${libs.versions.libfdxSnapshot.get()}")
        add(graalSourceSet.runtimeOnlyConfigurationName, "${libs.versions.libfdxGroup.get()}:vulkan_desktop:${libs.versions.libfdxSnapshot.get()}")
    } else {
        add(graalSourceSet.implementationConfigurationName, project(":libfdx:backends:desktop"))
        add(graalSourceSet.runtimeOnlyConfigurationName, project(":libfdx:extensions:graphics:gl:platform:desktop"))
        add(graalSourceSet.runtimeOnlyConfigurationName, project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
    }
    implementation(project(":benchmark:core"))
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_desktop_c:${libs.versions.libfdxSnapshot.get()}")
        runtimeOnly("${libs.versions.libfdxGroup.get()}:gl_desktop_c:${libs.versions.libfdxSnapshot.get()}")
        runtimeOnly("${libs.versions.libfdxGroup.get()}:vulkan_desktop_c:${libs.versions.libfdxSnapshot.get()}")
    } else {
        implementation(project(":libfdx:backends:desktop_c"))
        runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop_c"))
        runtimeOnly(project(":libfdx:extensions:graphics:vulkan:platform:desktop_c"))
    }
    add(cppSourceSet.implementationConfigurationName, project(":benchmark:core"))
    add(cppSourceSet.implementationConfigurationName, project(":libfdx:backends:desktop_cpp"))
    add(cppSourceSet.runtimeOnlyConfigurationName, project(":libfdx:extensions:graphics:gl:platform:desktop_cpp"))
    add(cppSourceSet.runtimeOnlyConfigurationName, project(":libfdx:extensions:graphics:vulkan:platform:desktop_cpp"))
}

// native-build-tools creates a self-project dependency during plugin application,
// which conflicts with the root publishing plugin's eager dependency evaluation.
// Keep the official Native Image CLI isolated to the Graal runtime classpath.
val graalOutputDirectory = layout.buildDirectory.dir("native/nativeGraalCompile")
val graalVmHome = providers.provider {
    val windowsX64 = System.getProperty("os.name", "").lowercase().contains("win") &&
        System.getProperty("os.arch", "").lowercase() in setOf("amd64", "x86_64")
    if (!windowsX64) throw GradleException(
        "The GraalVM benchmark metadata currently supports Windows x64. " +
            "Collect and review host-specific JNI, reflection, resource, and FFM metadata before enabling this host.")
    val configured = providers.environmentVariable("GRAALVM_HOME").orNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: providers.environmentVariable("JAVA_HOME").orNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("Set GRAALVM_HOME to a GraalVM JDK 25 installation with Native Image.")
    val home = file(configured)
    val nativeImage = home.resolve("bin/native-image" +
        if (System.getProperty("os.name", "").lowercase().contains("win")) ".cmd" else "")
    if (!nativeImage.isFile) throw GradleException(
        "Native Image was not found at $nativeImage. Set GRAALVM_HOME to a GraalVM JDK 25 installation; " +
            "JAVA_HOME is used only when GRAALVM_HOME is unset and it contains Native Image.")
    home
}
val graalInstrumentedOutputDirectory = layout.buildDirectory.dir("native/nativeGraalInstrumentedCompile")
val graalTrainingProfiles = listOf("gl", "vulkan").map { layout.buildDirectory.file("native/pgo/$it.iprof") }
val graalCpuIdentity = providers.environmentVariable("PROCESSOR_IDENTIFIER").orElse("unknown")
    .map { "${System.getProperty("os.arch")}:$it" }
val graalCompilationArguments = listOf(
    "--no-fallback", "-O3", "-march=native", "--enable-native-access=ALL-UNNAMED",
    "--initialize-at-run-time=org.lwjgl,io.github.libfdx.backend.desktop",
    "-J-Djdk.util.jar.enableMultiRelease=false", "-Djdk.util.jar.enableMultiRelease=false",
    "-Dorg.lwjgl.system.stackSize=1024"
)

fun Exec.configureGraalCompilation(outputDirectory: Provider<Directory>, instrumented: Boolean, usePgo: Boolean = true) {
    group = "build"
    dependsOn(tasks.named(graalSourceSet.classesTaskName), graalSourceSet.runtimeClasspath)
    inputs.files(graalSourceSet.runtimeClasspath)
    inputs.property("graalVmHome", graalVmHome.map { it.absolutePath })
    inputs.property("hostCpu", graalCpuIdentity)
    inputs.property("nativeImageArguments", graalCompilationArguments)
    inputs.property("instrumented", instrumented)
    inputs.property("usePgo", usePgo)
    inputs.file(graalVmHome.map { it.resolve("release") }).withPropertyName("graalVmRelease")
    inputs.file(buildFile).withPropertyName("graalBuildConfiguration")
    val windows = System.getProperty("os.name", "").lowercase().contains("win")
    inputs.file(graalVmHome.map { it.resolve("bin/native-image" + if (windows) ".cmd" else "") })
        .withPropertyName("nativeImageExecutable")
    // Native Image also emits JDK support libraries beside the executable.
    outputs.dir(outputDirectory)
    workingDir = rootProject.projectDir
    doFirst {
        val nativeImage = graalVmHome.get().resolve("bin/native-image" + if (windows) ".cmd" else "")
        val output = outputDirectory.get().asFile
        output.mkdirs()
        val argumentsFile = output.resolve("native-image.args")
        fun quoted(value: String) = "\"" + value.replace("\\", "/").replace("\"", "\\\"") + "\""
        // GraalVM 25 GA's Windows sampler can fail with WaitForSingleObject error 0x57.
        // Keep counter instrumentation; official workaround: https://github.com/oracle/graal/issues/11062.
        val profileArguments = if (instrumented) listOf("--pgo-instrument",
            "-H:+UnlockExperimentalVMOptions", "-H:-SamplingCollect", "-H:-UnlockExperimentalVMOptions")
        else if (usePgo) listOf(quoted("--pgo=" + graalTrainingProfiles.joinToString(",") { it.get().asFile.absolutePath }))
        else emptyList()
        argumentsFile.writeText((graalCompilationArguments + profileArguments + listOf(
            "-cp", quoted(graalSourceSet.runtimeClasspath.asPath),
            "-o", quoted(output.resolve("libfdx-benchmark-desktop-graal").absolutePath),
            "io.github.libfdx.benchmark.desktopgraal.DesktopGraalBenchmarkLauncher"
        )).joinToString("\n"))
        if (windows) commandLine("cmd.exe", "/d", "/c", nativeImage.absolutePath, "@${argumentsFile.absolutePath}")
        else commandLine(nativeImage.absolutePath, "@${argumentsFile.absolutePath}")
    }
}

tasks.register<Exec>("benchmark_desktop_graal_cpu_build_release") {
    description = "Builds the CPU comparison with GraalVM -O3 and host CPU instructions, without profiles from the GPU stress workload."
    configureGraalCompilation(layout.buildDirectory.dir("native/cpuComparison"), instrumented = false, usePgo = false)
}

val nativeGraalInstrumentedCompile = tasks.register<Exec>("nativeGraalInstrumentedCompile") {
    description = "Builds a Windows x64 PGO training image for this host CPU; requires compatible CPU features to run."
    configureGraalCompilation(graalInstrumentedOutputDirectory, instrumented = true)
}
val graalTrainingTasks = listOf("gl", "vulkan").mapIndexed { index, graphics ->
    tasks.register<Exec>("benchmark_desktop_graal_train_$graphics") {
        group = "benchmark"
        description = "Trains PGO on $graphics for 30 s at 1,500,000 sprites using this host CPU and graphics provider."
        dependsOn(nativeGraalInstrumentedCompile)
        mustRunAfter(listOf("debug", "release").flatMap { mode ->
            listOf("desktop_c", "desktop_cpp").map { ":benchmark:platform:plugin:libfdx_${it}_build_$mode" }
        })
        if (index > 0) dependsOn("benchmark_desktop_graal_train_gl")
        inputs.dir(graalInstrumentedOutputDirectory).withPropertyName("instrumentedImage")
        inputs.files(graalSourceSet.runtimeClasspath).withPropertyName("trainingClasspath")
        inputs.dir(rootProject.file("benchmark/assets")).withPropertyName("workloadAssets")
        val profile = graalTrainingProfiles[index]
        val result = layout.buildDirectory.file("native/pgo/$graphics.properties")
        val trainingArguments = listOf("--benchmark=sprite_batch_stress", "--graphics=$graphics",
            "--seconds=30", "--warmupSeconds=10", "--sprites=1500000", "--visible=true",
            "--vsync=false", "--foregroundFps=0")
        inputs.property("trainingArguments", trainingArguments)
        outputs.files(profile, result)
        workingDir = rootProject.projectDir
        doFirst {
            val profileFile = profile.get().asFile
            val resultFile = result.get().asFile
            profileFile.parentFile.mkdirs()
            listOf(profileFile, resultFile).forEach { previous ->
                if (previous.exists() && !previous.delete()) throw GradleException("Cannot replace PGO training output: $previous")
            }
            val executable = graalInstrumentedOutputDirectory.get().file("libfdx-benchmark-desktop-graal.exe").asFile
            commandLine(listOf(executable.absolutePath, "-XX:ProfilesDumpFile=${profileFile.absolutePath}") +
                trainingArguments + "--result=${resultFile.absolutePath}")
        }
        doLast {
            val profileFile = profile.get().asFile
            val resultFile = result.get().asFile
            if (!profileFile.isFile || profileFile.length() == 0L) throw GradleException("Missing or empty PGO profile: $profileFile")
            if (!resultFile.isFile) throw GradleException("Missing PGO training result: $resultFile")
            val properties = Properties().also { resultFile.inputStream().use(it::load) }
            if (properties.getProperty("completed") != "true" || properties.getProperty("sprites") != "1500000" ||
                properties.getProperty("graphicsProvider") != graphics ||
                (properties.getProperty("measuredSeconds")?.toDoubleOrNull() ?: 0.0) < 19.0) {
                throw GradleException("PGO training did not complete the representative $graphics workload: $resultFile")
            }
            println("Validated $graphics PGO profile (${profileFile.length()} bytes): $profileFile")
        }
    }
}
tasks.register<Exec>("nativeGraalCompile") {
    description = "Builds the Windows x64 release image trained on GL/Vulkan PGO for this host CPU; requires compatible CPU features."
    configureGraalCompilation(graalOutputDirectory, instrumented = false)
    dependsOn(graalTrainingTasks)
    inputs.files(graalTrainingProfiles).withPropertyName("trainingProfiles")
}
tasks.register("benchmark_desktop_graal_build_release") {
    group = "benchmark"
    description = "Builds the Windows x64 GraalVM benchmark trained on GL/Vulkan PGO for this host CPU (GRAALVM_HOME and Visual C++)."
    dependsOn("nativeGraalCompile")
}

listOf("gl", "vulkan").forEach { graphics ->
    tasks.register<JavaExec>("benchmark_desktop_graal_trace_$graphics") {
        group = "benchmark"
        description = "Collects GraalVM tracing-agent metadata for $graphics into build/native/agent-output."
        classpath = graalSourceSet.runtimeClasspath
        mainClass.set("io.github.libfdx.benchmark.desktopgraal.DesktopGraalBenchmarkLauncher")
        workingDir = rootProject.projectDir
        jvmArgs("--enable-native-access=ALL-UNNAMED", "-Djdk.util.jar.enableMultiRelease=false",
            "-Dorg.lwjgl.system.stackSize=1024")
        val metadata = layout.buildDirectory.dir("native/agent-output/$graphics")
        val result = layout.buildDirectory.file("native/agent-output/$graphics.properties")
        doFirst {
            setExecutable(graalVmHome.get().resolve("bin/java" +
                if (System.getProperty("os.name", "").lowercase().contains("win")) ".exe" else "").absolutePath)
            metadata.get().asFile.mkdirs()
            jvmArgs("-agentlib:native-image-agent=config-merge-dir=${metadata.get().asFile.absolutePath}")
        }
        args("--graphics=$graphics", "--seconds=1", "--warmupSeconds=0.5", "--visible=false",
            "--result=${result.get().asFile.absolutePath}")
    }
}

fun benchmarkPluginTask(name: String): String {
    return ":benchmark:platform:plugin:$name"
}

tasks.register("benchmark_libfdx_native_gl_release") {
    group = "benchmark"
    description = "Compares the libFDX CPU sprite benchmark on TeaVM C, jNative and GraalVM through libFDX OpenGL."
    dependsOn(benchmarkPluginTask("benchmark_libfdx_native_gl_release"))
}

fun registerBenchmarkAlias(name: String, descriptionText: String) {
    tasks.register(name) {
        group = "benchmark"
        description = descriptionText
        dependsOn(benchmarkPluginTask(name))
    }
}

tasks.register("benchmark_desktop_c_generate_debug") {
    group = "benchmark"
    description = "Generates the desktop_c benchmark project for Debug builds."
    dependsOn(benchmarkPluginTask("libfdx_desktop_c_generate"))
}

tasks.register("benchmark_desktop_c_generate_release") {
    group = "benchmark"
    description = "Generates the desktop_c benchmark project for Release builds."
    dependsOn(benchmarkPluginTask("libfdx_desktop_c_generate"))
}

tasks.register("benchmark_desktop_c_build_debug") {
    group = "benchmark"
    description = "Builds the desktop_c benchmark Debug executable."
    dependsOn(benchmarkPluginTask("libfdx_desktop_c_build_debug"))
}

tasks.register("benchmark_desktop_c_build_release") {
    group = "benchmark"
    description = "Builds the desktop_c benchmark Release executable."
    dependsOn(benchmarkPluginTask("libfdx_desktop_c_build_release"))
}

registerBenchmarkAlias(
    "benchmark_desktop_c_gl_debug",
    "Runs the desktop_c OpenGL benchmark suite in Debug and generates Markdown reports."
)
registerBenchmarkAlias(
    "benchmark_desktop_c_gl_release",
    "Runs the desktop_c OpenGL benchmark suite in Release and generates Markdown reports."
)
registerBenchmarkAlias(
    "benchmark_desktop_c_vulkan_debug",
    "Runs the desktop_c Vulkan benchmark suite in Debug and generates Markdown reports."
)
registerBenchmarkAlias(
    "benchmark_desktop_c_vulkan_release",
    "Runs the desktop_c Vulkan benchmark suite in Release and generates Markdown reports."
)
registerBenchmarkAlias(
    "benchmark_desktop_c_debug",
    "Runs the full desktop_c Debug benchmark suite and generates Markdown reports."
)
registerBenchmarkAlias(
    "benchmark_desktop_c_release",
    "Runs the full desktop_c Release benchmark suite and generates Markdown reports."
)

listOf("gl", "vulkan").forEach { graphics ->
    registerBenchmarkAlias("benchmark_desktop_graal_${graphics}_release",
        "Runs the GraalVM $graphics release benchmark suite and generates Markdown reports.")
}
registerBenchmarkAlias("benchmark_desktop_graal_release",
    "Runs the full GraalVM release benchmark suite and generates Markdown reports.")

listOf("debug", "release").forEach { mode ->
    listOf("generate", "build").forEach { action ->
        tasks.register("benchmark_desktop_cpp_${action}_$mode") {
            group = "benchmark"
            description = "Runs desktop_cpp benchmark $action for $mode with jNative."
            dependsOn(benchmarkPluginTask("libfdx_desktop_cpp_${action}_$mode"))
        }
    }
    listOf("gl", "vulkan").forEach { graphics ->
        registerBenchmarkAlias("benchmark_desktop_cpp_${graphics}_$mode",
            "Runs the desktop_cpp $graphics benchmark suite in $mode and generates Markdown reports.")
    }
    registerBenchmarkAlias("benchmark_desktop_cpp_$mode",
        "Runs the full desktop_cpp $mode benchmark suite and generates Markdown reports.")
    registerBenchmarkAlias("benchmark_desktop_native_$mode",
        "Runs the desktop native $mode benchmark suites and generates Markdown reports.")
}
