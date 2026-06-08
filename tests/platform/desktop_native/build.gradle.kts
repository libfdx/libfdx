import io.github.libfdx.build.LibExt

plugins {
    id("java")
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.tests"


val nativeTargetFileName = "libfdx-tests-vulkan-desktop-native"
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

libfdx {
    desktopNative {
        mainClass.set("io.github.libfdx.tests.desktopnative.DesktopNativeVulkanTestLauncher")
        targetFileName.set(nativeTargetFileName)
        buildType.set("Debug")
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

tasks.register("test_desktop_native_vulkan_debug_build") {
    group = "application"
    description = "Builds the desktop_native Vulkan graphics test Debug executable."
    dependsOn("libfdx_desktop_native_build_debug")
}

tasks.register("test_desktop_native_vulkan_release_build") {
    group = "application"
    description = "Builds the desktop_native Vulkan graphics test Release executable."
    dependsOn("libfdx_desktop_native_build_release")
}

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
