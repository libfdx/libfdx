import io.github.libfdx.build.LibExt

import org.gradle.api.file.FileCollection
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.file.Path

plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.tests"


val nativeTargetFileName = "libfdx-tests-vulkan-desktop-native"
val nativeBuildRoot = layout.buildDirectory.dir("dist/desktop-native")
val nativeShowConsole = providers.gradleProperty("libfdx.desktopNative.showConsole")
        .map { value -> value.toBooleanStrictOrNull() ?: value.toBoolean() }
        .orElse(true)
val nativeOpenConsole = providers.gradleProperty("libfdx.desktopNative.openConsole")
        .map { value -> value.toBooleanStrictOrNull() ?: value.toBoolean() }
        .orElse(true)

base {
    archivesName.set("tests_desktop_native")
}

dependencies {
    implementation(project(":tests:core"))
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_desktop_native:${LibExt.publishedLibfdxVersion}")

        runtimeOnly("${LibExt.fdxGroup}:vulkan_desktop_native:${LibExt.publishedLibfdxVersion}")
    } else {
        implementation(project(":libfdx:backends:desktop_native"))

        runtimeOnly(project(":libfdx:extensions:graphics:vulkan:platform:desktop_native"))
    }
}

val builderClasspath = sourceSets["main"].runtimeClasspath

fun isWindowsHost(): Boolean {
    return System.getProperty("os.name", "").lowercase().contains("win")
}

fun nativeBuildScript(scriptBaseName: String): File {
    return nativeBuildRoot.get().asFile.resolve(scriptBaseName + if(isWindowsHost()) ".bat" else ".sh")
}

fun registerNativeGenerateTask(taskName: String, nativeBuildType: String) {
    tasks.register(taskName) {
        group = "application"
        description = "Generates the desktop_native Vulkan graphics test $nativeBuildType project."
        dependsOn(builderClasspath)
        inputs.files(builderClasspath)
        outputs.dir(nativeBuildRoot)
        doLast {
            runNativeBuilder(
                    builderClasspath,
                    "io.github.libfdx.tests.desktopnative.DesktopNativeVulkanTestLauncher",
                    nativeBuildRoot.get().asFile,
                    nativeTargetFileName,
                    nativeBuildType,
                    nativeShowConsole.get(),
                    64,
                    1024)
        }
    }
}

fun registerNativeBuildTask(taskName: String, descriptionText: String, generateTask: String, scriptBaseName: String) {
    tasks.register<Exec>(taskName) {
        group = "application"
        description = descriptionText
        dependsOn(generateTask)
        doFirst {
            val script = nativeBuildScript(scriptBaseName)
            if(!script.isFile) {
                throw GradleException("Native build script was not generated: ${script.absolutePath}")
            }
            workingDir = nativeBuildRoot.get().asFile
            commandLine(if(isWindowsHost()) listOf("cmd", "/c", script.absolutePath)
                    else listOf("bash", script.absolutePath))
        }
    }
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

fun registerDesktopNativeVulkanTestTask(taskName: String, descriptionText: String, nativeBuildTask: String,
        nativeBuildType: String, defaultTestName: String, defaultFrames: String) {
    tasks.register<Exec>(taskName) {
        group = "application"
        description = descriptionText
        dependsOn(nativeBuildTask)
        workingDir = rootProject.projectDir
        doFirst {
            val suffix = if(nativeBuildType.equals("release", ignoreCase = true)) "release" else "debug"
            val executable = layout.buildDirectory.file(
                    "dist/desktop-native/c/release/$nativeTargetFileName" + "_$suffix"
                            + if(isWindowsHost()) ".exe" else "").get().asFile
            if(!executable.isFile) {
                throw GradleException("Native executable was not built: ${executable.absolutePath}")
            }
            val args = mutableListOf(
                    "--test=" + System.getProperty("libfdx.test.name", defaultTestName),
                    "--frames=" + System.getProperty("libfdx.test.frames", defaultFrames),
                    "--validate=" + System.getProperty("libfdx.test.validate", "false"),
                    "--driveInput=" + System.getProperty("libfdx.test.driveInput", "false"),
                    "--vsync=" + System.getProperty("libfdx.test.vsync", "true"),
                    "--visible=" + System.getProperty("libfdx.test.visible", "true"),
                    "--foregroundFps=" + System.getProperty("libfdx.test.foregroundFps", "0"))
            addSystemPropertyArg(args, "width", "libfdx.test.width")
            addSystemPropertyArg(args, "height", "libfdx.test.height")
            addSystemPropertyArg(args, "desktopImageCapture", "libfdx.test.desktopImageCapture")
            addSystemPropertyArg(args, "capture", "libfdx.test.capture")
            addSystemPropertyArg(args, "captureEvery", "libfdx.test.captureEvery")
            addSystemPropertyArg(args, "captureFrame", "libfdx.test.captureFrame")
            addSystemPropertyArg(args, "visualValidate", "libfdx.test.visualValidate")
            addSystemPropertyArg(args, "visualCaptureAllScenarios", "libfdx.test.visualCaptureAllScenarios")
            addSystemPropertyArg(args, "visualBaselineDir", "libfdx.test.visualBaselineDir")
            addSystemPropertyArg(args, "visualBaselineTemplate", "libfdx.test.visualBaselineTemplate")
            addSystemPropertyArg(args, "visualRequireBaselines", "libfdx.test.visualRequireBaselines")
            addSystemPropertyArg(args, "visualMismatchRatio", "libfdx.test.visualMismatchRatio")
            addSystemPropertyArg(args, "visualChannelTolerance", "libfdx.test.visualChannelTolerance")
            addSystemPropertyArg(args, "uiScale", "libfdx.test.uiScale")
            addSystemPropertyArg(args, "safeArea", "libfdx.test.safeArea")
            addSystemPropertyArg(args, "uiDebugLines", "libfdx.test.uiDebugLines")
            addSystemPropertyArg(args, "uiSection", "libfdx.test.uiSection")
            addSystemPropertyArg(args, "hoverLabel", "libfdx.test.hoverLabel")
            addSystemPropertyArg(args, "fpsLogSeconds", "libfdx.test.fpsLogSeconds")
            val stepDelaySeconds = System.getProperty("libfdx.validation.stepDelaySeconds")
            if (!stepDelaySeconds.isNullOrBlank()) {
                args.add("--stepDelaySeconds=$stepDelaySeconds")
            }
            addSystemPropertyArg(args, "reportEveryFrames", "libfdx.test.reportEveryFrames")
            addSystemPropertyArg(args, "stallFrameMs", "libfdx.test.stallFrameMs")
            addSystemPropertyArg(args, "stallLimit", "libfdx.test.stallLimit")
            if(isWindowsHost() && nativeOpenConsole.get()) {
                commandLine(windowsPowerShellStartCommand(executable, args, rootProject.projectDir))
            }
            else {
                commandLine(listOf(executable.absolutePath) + args)
            }
        }
    }
}

fun addSystemPropertyArg(args: MutableList<String>, option: String, property: String) {
    val value = System.getProperty(property)
    if(!value.isNullOrBlank()) {
        args.add("--$option=$value")
    }
}

fun runNativeBuilder(classpath: FileCollection, mainClassName: String, buildRoot: File, targetFileName: String,
        nativeBuildType: String, showConsole: Boolean, minHeapSize: Int, maxHeapSize: Int) {
    withBuilderClassLoader(classpath) { classLoader ->
        val builderClass = classLoader.loadClass("io.github.libfdx.backend.desktopnative.NativeBuilder")
        var builder = builderClass.getMethod("desktop").invoke(null)
        val paths = classpath.files.map { it.toPath() }
        builder = invokeBuilder(builder, "classpath", listOf(Collection::class.java), listOf(paths))
        builder = invokeBuilder(builder, "nativeResourceClasspath", listOf(Collection::class.java), listOf(paths))
        builder = invokeBuilder(builder, "buildRoot", listOf(Path::class.java), listOf(buildRoot.toPath()))
        builder = invokeBuilder(builder, "mainClass", listOf(String::class.java), listOf(mainClassName))
        builder = invokeBuilder(builder, "targetFileName", listOf(String::class.java), listOf(targetFileName))
        builder = invokeBuilder(builder, "buildType", listOf(String::class.java), listOf(nativeBuildType))
        builder = invokeBuilder(builder, "showConsole", listOf(Boolean::class.javaPrimitiveType!!), listOf(showConsole))
        builder = invokeBuilder(builder, "minHeapSize", listOf(Integer.TYPE), listOf(minHeapSize))
        builder = invokeBuilder(builder, "maxHeapSize", listOf(Integer.TYPE), listOf(maxHeapSize))
        invokeBuilder(builder, "build", emptyList(), emptyList())
    }
}

fun withBuilderClassLoader(classpath: FileCollection, action: (ClassLoader) -> Unit) {
    val urls = classpath.files.map { it.toURI().toURL() }.toTypedArray()
    URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { classLoader ->
        val previous = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = classLoader
        try {
            action(classLoader)
        }
        finally {
            Thread.currentThread().contextClassLoader = previous
        }
    }
}

fun invokeBuilder(target: Any, methodName: String, parameterTypes: List<Class<*>>, args: List<Any>): Any {
    try {
        return target.javaClass.getMethod(methodName, *parameterTypes.toTypedArray())
                .invoke(target, *args.toTypedArray()) ?: target
    }
    catch (error: InvocationTargetException) {
        val cause = error.targetException
        if (cause is RuntimeException) {
            throw cause
        }
        if (cause is Error) {
            throw cause
        }
        throw GradleException("Builder method '$methodName' failed.", cause)
    }
}

registerNativeGenerateTask("test_desktop_native_vulkan_debug_generate", "Debug")
registerNativeGenerateTask("test_desktop_native_vulkan_release_generate", "Release")

registerNativeBuildTask("test_desktop_native_vulkan_debug_build",
        "Builds the desktop_native Vulkan graphics test Debug executable.",
        "test_desktop_native_vulkan_debug_generate",
        "app_debug")

registerNativeBuildTask("test_desktop_native_vulkan_release_build",
        "Builds the desktop_native Vulkan graphics test Release executable.",
        "test_desktop_native_vulkan_release_generate",
        "app_release")

registerDesktopNativeVulkanTestTask(
        "test_desktop_native_vulkan_debug_run",
        "Runs graphics tests with desktop_native Vulkan using the Debug native executable.",
        "test_desktop_native_vulkan_debug_build",
        "Debug",
        "",
        "0")

registerDesktopNativeVulkanTestTask(
        "test_desktop_native_vulkan_release_run",
        "Runs graphics tests with desktop_native Vulkan using the Release native executable.",
        "test_desktop_native_vulkan_release_build",
        "Release",
        "",
        "0")
