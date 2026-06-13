import io.github.libfdx.build.LibExt

import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.tests"


val glRuntimeClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val vulkanRuntimeClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val wgpuRuntimeClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

base {
    archivesName.set("tests_desktop")
}

dependencies {
    implementation(project(":tests:core"))
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_desktop:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:wgpu_core:${LibExt.publishedLibfdxVersion}")

        glRuntimeClasspath("${LibExt.fdxGroup}:gl_desktop:${LibExt.publishedLibfdxVersion}")
        vulkanRuntimeClasspath("${LibExt.fdxGroup}:vulkan_desktop:${LibExt.publishedLibfdxVersion}")
        wgpuRuntimeClasspath("${LibExt.fdxGroup}:wgpu_desktop_ffm:${LibExt.publishedLibfdxVersion}")
    } else {
        implementation(project(":libfdx:backends:desktop"))
        implementation(project(":libfdx:extensions:graphics:wgpu:core"))

        glRuntimeClasspath(project(":libfdx:extensions:graphics:gl:platform:desktop"))
        vulkanRuntimeClasspath(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
        wgpuRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
    }
}

val desktopRuntimeClasspath = glRuntimeClasspath + wgpuRuntimeClasspath + vulkanRuntimeClasspath
val desktopGraphicsOptions = "gl,wgpu,vulkan"
val testLauncherMainClass = "io.github.libfdx.tests.desktop.DesktopTestLauncher"
val applicationGroup = "application"
val applicationTestGroup = "application_test"
val desktopJvmDistDir = layout.buildDirectory.dir("dist/desktop-jvm")

val desktopBackendResources = if (LibExt.usePublishedLibfdx) {
    files()
} else {
    files(project(":libfdx:backends:desktop").layout.buildDirectory.dir("resources/main"))
}

val desktopForwardedTestProperties = listOf(
        "libfdx.test.name",
        "libfdx.test.mode",
        "libfdx.test.frames",
        "libfdx.test.validate",
        "libfdx.test.driveInput",
        "libfdx.test.visualValidate",
        "libfdx.test.visible",
        "libfdx.test.vsync",
        "libfdx.test.foregroundFps",
        "libfdx.test.fpsLogSeconds",
        "libfdx.test.width",
        "libfdx.test.height",
        "libfdx.test.capture",
        "libfdx.test.captureEvery",
        "libfdx.test.captureFrame",
        "libfdx.test.visualBaselineDir",
        "libfdx.test.visualRequireBaselines",
        "libfdx.test.visualMismatchRatio",
        "libfdx.test.visualChannelTolerance",
        "libfdx.test.visualBaselineTemplate",
        "libfdx.test.uiScale",
        "libfdx.test.safeArea",
        "libfdx.test.uiDebugLines",
        "libfdx.test.uiSection",
        "libfdx.test.hoverLabel",
        "libfdx.test.reportEveryFrames",
        "libfdx.test.stallFrameMs",
        "libfdx.test.stallLimit"
)

fun registerDesktopTestBuild(providerName: String, displayName: String) {
    val taskBaseName = "test_desktop_$providerName"
    val releaseClasspath = sourceSets["main"].runtimeClasspath + desktopRuntimeClasspath
    val launchDefaults = layout.buildDirectory.file(
            "generated/desktop-jvm/$taskBaseName/libfdx-desktop-launch.properties")
    val writeLaunchDefaults = tasks.register("${taskBaseName}_write_launch_defaults") {
        outputs.file(launchDefaults)
        doLast {
            val output = launchDefaults.get().asFile
            output.parentFile.mkdirs()
            output.writeText(
                    "graphics=$providerName${System.lineSeparator()}graphicsLabel=$displayName${System.lineSeparator()}",
                    Charsets.UTF_8)
        }
    }
    tasks.register<Jar>("${taskBaseName}_build") {
        group = applicationGroup
        description = "Builds the desktop graphics test $displayName release jar."
        dependsOn("classes", releaseClasspath, writeLaunchDefaults)
        archiveFileName.set("$taskBaseName.jar")
        destinationDirectory.set(desktopJvmDistDir)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        isZip64 = true
        manifest {
            attributes(
                    "Main-Class" to testLauncherMainClass,
                    "Multi-Release" to "true",
                    "Enable-Native-Access" to "ALL-UNNAMED")
        }
        exclude("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF")
        from({
            releaseClasspath.files
                    .filter { it.exists() }
                    .map { if (it.isDirectory) it else zipTree(it) }
        })
        from(launchDefaults.map { it.asFile }) {
            rename { "libfdx-desktop-launch.properties" }
        }
    }
}

registerDesktopTestBuild("gl", "GL")
registerDesktopTestBuild("wgpu", "WGPU")
registerDesktopTestBuild("vulkan", "Vulkan")

fun JavaExec.configureTestRun(
    descriptionText: String,
    graphics: String,
    graphicsLabel: String,
    providerClasspath: FileCollection,
    defaultFrames: String,
    defaultValidate: String = "true",
    defaultDriveInput: String = "false",
    defaultVisualValidate: String = "false",
    taskGroup: String = applicationGroup
) {
    val runSystemProperties = gradle.startParameter.systemPropertiesArgs
    fun prop(name: String, fallback: String): String = runSystemProperties[name] ?: fallback

    group = taskGroup
    description = descriptionText
    classpath = sourceSets["main"].runtimeClasspath + providerClasspath
    mainClass.set(testLauncherMainClass)
    workingDir = rootProject.projectDir
    runSystemProperties
            .filterKeys { it.startsWith("libfdx.validation.") }
            .forEach { (name, value) ->
                if (value.isNotBlank()) {
                    systemProperty(name, value)
                }
            }
    val testName = System.getProperty("libfdx.test.name")
    if (!testName.isNullOrBlank()) {
        systemProperty("libfdx.test.name", testName)
    }
    val testMode = prop("libfdx.test.mode", "")
    if (!testMode.isNullOrBlank()) {
        systemProperty("libfdx.test.mode", testMode)
    }
    systemProperty("libfdx.test.graphics", graphics)
    systemProperty("libfdx.test.graphicsLabel", graphicsLabel)
    systemProperty("libfdx.test.graphicsOptions", prop("libfdx.test.graphicsOptions", desktopGraphicsOptions))
    systemProperty("libfdx.test.frames", prop("libfdx.test.frames", defaultFrames))
    systemProperty("libfdx.test.validate", prop("libfdx.test.validate", defaultValidate))
    systemProperty("libfdx.test.driveInput", prop("libfdx.test.driveInput", defaultDriveInput))
    systemProperty("libfdx.test.visualValidate",
            prop("libfdx.test.visualValidate", defaultVisualValidate))
    systemProperty("libfdx.test.visible", prop("libfdx.test.visible", "true"))
    val visualBaselineDir = prop("libfdx.test.visualBaselineDir", "")
    if (!visualBaselineDir.isNullOrBlank()) {
        systemProperty("libfdx.test.visualBaselineDir", visualBaselineDir)
    }
    val visualRequireBaselines = prop("libfdx.test.visualRequireBaselines", "")
    if (!visualRequireBaselines.isNullOrBlank()) {
        systemProperty("libfdx.test.visualRequireBaselines", visualRequireBaselines)
    }
    val visualMismatchRatio = prop("libfdx.test.visualMismatchRatio", "")
    if (!visualMismatchRatio.isNullOrBlank()) {
        systemProperty("libfdx.test.visualMismatchRatio", visualMismatchRatio)
    }
    val visualChannelTolerance = prop("libfdx.test.visualChannelTolerance", "")
    if (!visualChannelTolerance.isNullOrBlank()) {
        systemProperty("libfdx.test.visualChannelTolerance", visualChannelTolerance)
    }
    val visualBaselineTemplate = prop("libfdx.test.visualBaselineTemplate", "")
    if (!visualBaselineTemplate.isNullOrBlank()) {
        systemProperty("libfdx.test.visualBaselineTemplate", visualBaselineTemplate)
    }
    systemProperty("libfdx.test.vsync", prop("libfdx.test.vsync", "true"))
    systemProperty("libfdx.test.foregroundFps", prop("libfdx.test.foregroundFps", "0"))
    val fpsLogSeconds = prop("libfdx.test.fpsLogSeconds", "")
    if (!fpsLogSeconds.isNullOrBlank()) {
        systemProperty("libfdx.test.fpsLogSeconds", fpsLogSeconds)
    }
    val testWidth = prop("libfdx.test.width", "")
    if (!testWidth.isNullOrBlank()) {
        systemProperty("libfdx.test.width", testWidth)
    }
    val testHeight = prop("libfdx.test.height", "")
    if (!testHeight.isNullOrBlank()) {
        systemProperty("libfdx.test.height", testHeight)
    }
    val capturePath = prop("libfdx.test.capture", "")
    if (!capturePath.isNullOrBlank()) {
        systemProperty("libfdx.test.capture", capturePath)
    }
    val captureEvery = prop("libfdx.test.captureEvery", "")
    if (!captureEvery.isNullOrBlank()) {
        systemProperty("libfdx.test.captureEvery", captureEvery)
    }
    val captureFrame = prop("libfdx.test.captureFrame", "")
    if (!captureFrame.isNullOrBlank()) {
        systemProperty("libfdx.test.captureFrame", captureFrame)
    }
    val uiScale = prop("libfdx.test.uiScale", "")
    if (!uiScale.isNullOrBlank()) {
        systemProperty("libfdx.test.uiScale", uiScale)
    }
    val safeArea = prop("libfdx.test.safeArea", "")
    if (!safeArea.isNullOrBlank()) {
        systemProperty("libfdx.test.safeArea", safeArea)
    }
    val uiDebugLines = prop("libfdx.test.uiDebugLines", "")
    if (!uiDebugLines.isNullOrBlank()) {
        systemProperty("libfdx.test.uiDebugLines", uiDebugLines)
    }
    val uiSection = prop("libfdx.test.uiSection", "")
    if (!uiSection.isNullOrBlank()) {
        systemProperty("libfdx.test.uiSection", uiSection)
    }
    val hoverLabel = prop("libfdx.test.hoverLabel", "")
    if (!hoverLabel.isNullOrBlank()) {
        systemProperty("libfdx.test.hoverLabel", hoverLabel)
    }
    val reportEveryFrames = prop("libfdx.test.reportEveryFrames", "")
    if (!reportEveryFrames.isNullOrBlank()) {
        systemProperty("libfdx.test.reportEveryFrames", reportEveryFrames)
    }
    val stallFrameMs = prop("libfdx.test.stallFrameMs", "")
    if (!stallFrameMs.isNullOrBlank()) {
        systemProperty("libfdx.test.stallFrameMs", stallFrameMs)
    }
    val stallLimit = prop("libfdx.test.stallLimit", "")
    if (!stallLimit.isNullOrBlank()) {
        systemProperty("libfdx.test.stallLimit", stallLimit)
    }
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

tasks.register<JavaExec>("test_desktop_gl_run") {
    configureTestRun("Runs graphics tests with desktop GL.",
            "gl", "GL", desktopRuntimeClasspath,
            "0", defaultValidate = "false")
    dependsOn("test_desktop_gl_build")
    useJava25Launcher()
}

tasks.register<JavaExec>("test_desktop_wgpu_run") {
    configureTestRun("Runs graphics tests with WGPU.",
            "wgpu", "WGPU", desktopRuntimeClasspath,
            "0", defaultValidate = "false")
    dependsOn("test_desktop_wgpu_build")
    useJava25Launcher()
}

tasks.register<JavaExec>("test_desktop_vulkan_run") {
    configureTestRun("Runs graphics tests with desktop Vulkan.",
            "vulkan", "Vulkan", desktopRuntimeClasspath,
            "0", defaultValidate = "false")
    dependsOn("test_desktop_vulkan_build")
    useJava25Launcher()
}

tasks.register<JavaExec>("test_math_acceleration_desktop") {
    group = applicationTestGroup
    description = "Builds and validates desktop runtime fdx SIMD math acceleration against scalar math."
    if (!LibExt.usePublishedLibfdx) {
        dependsOn(":libfdx:backends:desktop:processResources")
    }
    classpath = sourceSets["main"].output + sourceSets["main"].compileClasspath + desktopBackendResources
    mainClass.set("io.github.libfdx.backend.desktop.DesktopMathAccelerationCheck")
    workingDir = rootProject.projectDir
    systemProperty("libfdx.math.requireNative", "true")
    useJava25Launcher()
}

tasks.register<JavaExec>("test_wgpu_validate") {
    configureTestRun("Runs the UI validation scenario on desktop WGPU.",
            "wgpu", "WGPU", wgpuRuntimeClasspath,
            "19", defaultValidate = "true", defaultDriveInput = "true", taskGroup = applicationTestGroup)
    systemProperty("libfdx.test.capture", "build/reports/uikit/{scenario}-{frame}.png")
    useJava25Launcher()
}

tasks.register<JavaExec>("test_wgpu_validate_visual") {
    configureTestRun("Runs desktop WGPU UI automation with visual checks.",
            "wgpu", "WGPU", wgpuRuntimeClasspath,
            "19", defaultValidate = "true", defaultDriveInput = "true", defaultVisualValidate = "true",
            taskGroup = applicationTestGroup)
    systemProperty("libfdx.test.capture", "build/reports/uikit/{scenario}-{frame}.png")
    useJava25Launcher()
}

tasks.register<JavaExec>("test_wgpu_compare_visual") {
    configureTestRun("Compares desktop WGPU UI rendering against GL baseline captures.",
            "wgpu", "WGPU", wgpuRuntimeClasspath,
            "180", defaultValidate = "true", defaultDriveInput = "true", defaultVisualValidate = "true",
            taskGroup = applicationTestGroup)
    systemProperty("libfdx.test.capture", "build/reports/uikit/wgpu/{scenario}-{frame}.png")
    systemProperty("libfdx.test.captureEvery", "30")
    systemProperty("libfdx.test.visualCaptureAllScenarios", "true")
    systemProperty("libfdx.test.visualBaselineDir", "build/reports/uikit/gl-baseline")
    systemProperty("libfdx.test.visualBaselineTemplate", "{scenario}.png")
    systemProperty("libfdx.test.visualRequireBaselines", "true")
    useJava25Launcher()
}

tasks.register<JavaExec>("test_gl_validate") {
    configureTestRun("Runs the UI validation scenario on desktop GL.",
            "gl", "GL", glRuntimeClasspath,
            "19", defaultValidate = "true", defaultDriveInput = "true", taskGroup = applicationTestGroup)
    systemProperty("libfdx.test.capture", "build/reports/uikit/{scenario}-{frame}.png")
    useJava25Launcher()
}

tasks.register<JavaExec>("test_gl_validate_visual") {
    configureTestRun("Runs GL desktop UI automation with visual checks.",
            "gl", "GL", glRuntimeClasspath,
            "19", defaultValidate = "true", defaultDriveInput = "true", defaultVisualValidate = "true",
            taskGroup = applicationTestGroup)
    systemProperty("libfdx.test.capture", "build/reports/uikit/{scenario}-{frame}.png")
    useJava25Launcher()
}

tasks.register<JavaExec>("test_gl_validate_baseline") {
    configureTestRun("Captures deterministic GL UI baseline images for desktop visual parity.",
            "gl", "GL", glRuntimeClasspath,
            "180", defaultValidate = "true", defaultDriveInput = "true", defaultVisualValidate = "false",
            taskGroup = applicationTestGroup)
    systemProperty("libfdx.test.capture", "build/reports/uikit/gl-baseline/{scenario}.png")
    systemProperty("libfdx.test.captureEvery", "30")
    systemProperty("libfdx.test.visualBaselineDir", "build/reports/uikit/gl-baseline")
    systemProperty("libfdx.test.visualBaselineTemplate", "{scenario}.png")
    systemProperty("libfdx.test.visualCaptureAllScenarios", "true")
    useJava25Launcher()
}

tasks.register<JavaExec>("test_vulkan_compare_visual") {
    configureTestRun("Compares desktop Vulkan UI rendering against GL baseline captures.",
            "vulkan", "Vulkan", vulkanRuntimeClasspath,
            "180", defaultValidate = "true", defaultDriveInput = "true", defaultVisualValidate = "true",
            taskGroup = applicationTestGroup)
    systemProperty("libfdx.test.capture", "build/reports/uikit/vulkan/{scenario}-{frame}.png")
    systemProperty("libfdx.test.captureEvery", "30")
    systemProperty("libfdx.test.visualCaptureAllScenarios", "true")
    systemProperty("libfdx.test.visualBaselineDir", "build/reports/uikit/gl-baseline")
    systemProperty("libfdx.test.visualBaselineTemplate", "{scenario}.png")
    systemProperty("libfdx.test.visualRequireBaselines", "true")
    useJava25Launcher()
}

tasks.named<JavaExec>("test_wgpu_compare_visual") {
    mustRunAfter("test_gl_validate_baseline")
}

tasks.named<JavaExec>("test_vulkan_compare_visual") {
    mustRunAfter("test_gl_validate_baseline")
}

tasks.named<JavaExec>("test_vulkan_compare_visual") {
    mustRunAfter("test_wgpu_compare_visual")
}

tasks.register("test_ui_visual_parity_desktop") {
    group = applicationTestGroup
    description = "Captures GL baseline and compares WGPU/Vulkan desktop UI rendering."
    dependsOn(
            "test_gl_validate_baseline",
            "test_wgpu_compare_visual",
            "test_vulkan_compare_visual"
    )
    doLast {
        val report = file("$buildDir/reports/uikit/visual-parity-matrix.md")
        report.parentFile.mkdirs()
        val lines = StringBuilder()
        lines.append("visual-parity-matrix desktop\\n")
        lines.append("scope: desktop uikit ui test\\n")
        lines.append("status: PASS\\n")
        lines.append("steps:\\n")
        lines.append("- test_gl_validate_baseline: PASS\\n")
        lines.append("- test_wgpu_compare_visual: PASS\\n")
        lines.append("- test_vulkan_compare_visual: PASS\\n")
        lines.append("baselineDir: build/reports/uikit/gl-baseline\\n")
        report.writeText(lines.toString())
    }
}
