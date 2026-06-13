import io.github.libfdx.build.LibExt

import org.gradle.api.GradleException
import org.gradle.api.attributes.java.TargetJvmVersion
import org.teavm.gradle.api.OptimizationLevel

plugins {
    id("java")
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.tests"

base {
    archivesName.set("tests_plugin")
}

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

val desktopApplicationRuntimeClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

dependencies {
    implementation(project(":tests:platform:desktop"))
    implementation(project(":tests:platform:web"))
    implementation(project(":tests:platform:desktop_native"))
    implementation(project(":tests:platform:psp"))
    desktopApplicationRuntimeClasspath(project(":tests:platform:desktop"))

    if (LibExt.usePublishedLibfdx) {
        glRuntimeClasspath("${LibExt.fdxGroup}:gl_desktop:${LibExt.publishedLibfdxVersion}")
        vulkanRuntimeClasspath("${LibExt.fdxGroup}:vulkan_desktop:${LibExt.publishedLibfdxVersion}")
        wgpuRuntimeClasspath("${LibExt.fdxGroup}:wgpu_desktop_ffm:${LibExt.publishedLibfdxVersion}")
    } else {
        glRuntimeClasspath(project(":libfdx:extensions:graphics:gl:platform:desktop"))
        vulkanRuntimeClasspath(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
        wgpuRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
    }
}

val pspTestTasks = mapOf(
        "cube" to listOf("plugin_test_cube_generate", "plugin_test_cube_build", "plugin_test_cube_ppsspp_capture"),
        "spritebatch" to listOf("plugin_test_spritebatch_generate", "plugin_test_spritebatch_build",
                "plugin_test_spritebatch_ppsspp_capture"),
        "backend_clear" to listOf("plugin_test_backend_clear_generate", "plugin_test_backend_clear_build",
                "plugin_test_backend_clear_ppsspp_capture"),
        "backend_shape" to listOf("plugin_test_backend_shape_generate", "plugin_test_backend_shape_build",
                "plugin_test_backend_shape_ppsspp_capture"),
        "backend_ui_panel" to listOf("plugin_test_backend_ui_panel_generate",
                "plugin_test_backend_ui_panel_build", "plugin_test_backend_ui_panel_ppsspp_capture"),
        "backend_spritebatch" to listOf("plugin_test_backend_spritebatch_generate",
                "plugin_test_backend_spritebatch_build", "plugin_test_backend_spritebatch_ppsspp_capture"),
        "backend_input" to listOf("plugin_test_backend_input_generate", "plugin_test_backend_input_build",
                "plugin_test_backend_input_ppsspp_capture"),
        "backend_uikit" to listOf("plugin_test_backend_uikit_generate", "plugin_test_backend_uikit_build",
                "plugin_test_backend_uikit_ppsspp_capture"),
        "backend_uikit_smoke" to listOf("plugin_test_backend_uikit_smoke_generate",
                "plugin_test_backend_uikit_smoke_build", "plugin_test_backend_uikit_smoke_ppsspp_capture"),
)
val desktopNativeTaskNames = setOf(
        "plugin_test_desktop_native_vulkan_debug_generate",
        "plugin_test_desktop_native_vulkan_release_generate",
        "plugin_test_desktop_native_vulkan_debug_build",
        "plugin_test_desktop_native_vulkan_release_build",
        "plugin_test_desktop_native_vulkan_debug_run",
        "plugin_test_desktop_native_vulkan_release_run",
        "libfdx_desktop_native_generate",
        "libfdx_desktop_native_build_debug",
        "libfdx_desktop_native_build_release",
        "libfdx_desktop_native_run_debug",
        "libfdx_desktop_native_run_release",
)
val pspTaskNames = pspTestTasks.values.flatten().toSet() + setOf(
        "libfdx_psp_generate",
        "libfdx_psp_build",
        "libfdx_psp_ppsspp_capture",
)
val requestedTaskNames = gradle.startParameter.taskNames.map { it.substringAfterLast(":") }.toSet()
val selectedRequestedPspTests = pspTestTasks
        .filter { (_, tasks) -> tasks.any(requestedTaskNames::contains) }
        .keys
        .toList()
if (selectedRequestedPspTests.size > 1) {
    throw GradleException("Run PSP plugin test variants in separate Gradle invocations: $selectedRequestedPspTests")
}
val wantsPspTarget = requestedTaskNames.any(pspTaskNames::contains)
val wantsDesktopNativeTarget = requestedTaskNames.any(desktopNativeTaskNames::contains)
if (wantsPspTarget && wantsDesktopNativeTarget) {
    throw GradleException("Run desktop_native and PSP plugin tasks in separate Gradle invocations.")
}
val defaultPspTest = selectedRequestedPspTests.firstOrNull() ?: "cube"
val selectedPspTest = providers.gradleProperty("libfdx.psp.test").orElse(defaultPspTest)

fun pspMainClass(testName: String): String {
    return when (testName) {
        "cube" -> "io.github.libfdx.tests.psp.PspCubeTestLauncher"
        "spritebatch" -> "io.github.libfdx.tests.psp.PspSpriteBatchTestLauncher"
        "backend_clear" -> "io.github.libfdx.tests.psp.PspBackendClearTestLauncher"
        "backend_shape" -> "io.github.libfdx.tests.psp.PspBackendShapeTestLauncher"
        "backend_ui_panel" -> "io.github.libfdx.tests.psp.PspBackendUiPanelTestLauncher"
        "backend_spritebatch" -> "io.github.libfdx.tests.psp.PspBackendSpriteBatchTestLauncher"
        "backend_input" -> "io.github.libfdx.tests.psp.PspBackendInputTestLauncher"
        "backend_uikit" -> "io.github.libfdx.tests.psp.PspBackendUiKitTestLauncher"
        "backend_uikit_smoke" -> "io.github.libfdx.tests.psp.PspBackendUiKitSmokeTestLauncher"
        else -> throw GradleException("Unknown PSP plugin test '$testName'.")
    }
}

fun pspTargetFileName(testName: String): String {
    return when (testName) {
        "cube" -> "libfdx-plugin-tests-psp-cube"
        "spritebatch" -> "libfdx-plugin-psp-spritebatch"
        "backend_clear" -> "libfdx-plugin-psp-backend-clear"
        "backend_shape" -> "libfdx-plugin-psp-backend-shape"
        "backend_ui_panel" -> "libfdx-plugin-psp-backend-ui-panel"
        "backend_spritebatch" -> "libfdx-plugin-psp-backend2d"
        "backend_input" -> "libfdx-plugin-psp-backend-input"
        "backend_uikit" -> "libfdx-plugin-psp-backend-uikit"
        "backend_uikit_smoke" -> "libfdx-plugin-psp-backend-uikit-smoke"
        else -> throw GradleException("Unknown PSP plugin test '$testName'.")
    }
}

libfdx {
    assets(rootProject.layout.projectDirectory.dir("tests/assets"))
    bitmapFont("psp_test_bitmap") {
        sourceFile.set(rootProject.layout.projectDirectory.file("tests/assets/font/freetype/lsans.ttf"))
        outputDir.set(rootProject.layout.projectDirectory.dir("tests/assets"))
        assetPath.set("font/bitmap")
        size.set(24)
        padding.set(2)
        maxTextureSize.set(512)
    }
    desktopJvm {
        taskNamePrefix.set("plugin_test_desktop")
        mainClass.set("io.github.libfdx.tests.desktop.DesktopTestLauncher")
        runtimeClasspath(desktopApplicationRuntimeClasspath)
        forwardSystemPropertyPrefix("libfdx.test.")
        forwardSystemPropertyPrefix("libfdx.validation.")
        provider("gl") {
            displayName.set("GL")
            runtimeClasspath(glRuntimeClasspath)
            systemProperty("libfdx.test.graphics", "gl")
            systemProperty("libfdx.test.graphicsLabel", "GL")
            launchProperty("graphics", "gl")
            launchProperty("graphicsLabel", "GL")
            buildDescription.set("Builds the plugin-use desktop graphics test GL release jar.")
            runDescription.set("Runs the plugin-use desktop graphics test with GL.")
        }
        provider("wgpu") {
            displayName.set("WGPU")
            runtimeClasspath(wgpuRuntimeClasspath)
            systemProperty("libfdx.test.graphics", "wgpu")
            systemProperty("libfdx.test.graphicsLabel", "WGPU")
            launchProperty("graphics", "wgpu")
            launchProperty("graphicsLabel", "WGPU")
            buildDescription.set("Builds the plugin-use desktop graphics test WGPU release jar.")
            runDescription.set("Runs the plugin-use desktop graphics test with WGPU.")
        }
        provider("vulkan") {
            displayName.set("Vulkan")
            runtimeClasspath(vulkanRuntimeClasspath)
            systemProperty("libfdx.test.graphics", "vulkan")
            systemProperty("libfdx.test.graphicsLabel", "Vulkan")
            launchProperty("graphics", "vulkan")
            launchProperty("graphicsLabel", "Vulkan")
            buildDescription.set("Builds the plugin-use desktop graphics test Vulkan release jar.")
            runDescription.set("Runs the plugin-use desktop graphics test with Vulkan.")
        }
    }
    js {
        mainClass.set("io.github.libfdx.tests.web.WebTestJsLauncher")
        htmlTitle.set("libfdx Plugin Tests - WebGL JS")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)
    }
    wasm {
        mainClass.set("io.github.libfdx.tests.web.WebTestWasmLauncher")
        htmlTitle.set("libfdx Plugin Tests - WebGL Wasm")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)
    }
    if (wantsPspTarget) {
        psp {
            mainClass.set(selectedPspTest.map(::pspMainClass))
            targetFileName.set(selectedPspTest.map(::pspTargetFileName))
            optimization.set(OptimizationLevel.BALANCED)
            debugInformation.set(true)
            debugMemory.set(false)
            maxHeapSize.set(32)
        }
    } else {
        desktopNative {
            mainClass.set("io.github.libfdx.tests.desktopnative.DesktopNativeVulkanTestLauncher")
            targetFileName.set("libfdx-tests-plugin-vulkan-desktop-native")
            showConsole.set(providers.gradleProperty("libfdx.desktopNative.showConsole")
                    .map { value -> value.toBooleanStrictOrNull() ?: value.toBoolean() }
                    .orElse(true))
            minHeapSize.set(64)
            maxHeapSize.set(1024)
        }
    }
}

tasks.register("plugin_test_webgl_js_build") {
    group = "application"
    description = "Builds the plugin-use WebGL JavaScript test web application."
    dependsOn("libfdx_web_js_build")
}

tasks.register("plugin_test_webgl_wasm_build") {
    group = "application"
    description = "Builds the plugin-use WebGL Wasm test web application."
    dependsOn("libfdx_web_wasm_build")
}

tasks.register("plugin_test_webgpu_js_build") {
    group = "application"
    description = "Builds the plugin-use WebGPU JavaScript test web application."
    dependsOn("libfdx_web_js_build")
    configureWebGpuPage("dist/web-js/webapp", "libfdx Plugin Tests - WebGPU JS")
}

tasks.register("plugin_test_webgpu_wasm_build") {
    group = "application"
    description = "Builds the plugin-use WebGPU Wasm test web application."
    dependsOn("libfdx_web_wasm_build")
    configureWebGpuPage("dist/web-wasm/webapp", "libfdx Plugin Tests - WebGPU Wasm")
}

tasks.register("plugin_test_desktop_native_vulkan_debug_generate") {
    group = "application"
    description = "Generates the plugin-use desktop_native Vulkan graphics test Debug project."
    dependsOn("libfdx_desktop_native_generate")
}

tasks.register("plugin_test_desktop_native_vulkan_release_generate") {
    group = "application"
    description = "Generates the plugin-use desktop_native Vulkan graphics test Release project."
    dependsOn("libfdx_desktop_native_generate")
}

tasks.register("plugin_test_desktop_native_vulkan_debug_build") {
    group = "application"
    description = "Builds the plugin-use desktop_native Vulkan graphics test Debug executable."
    dependsOn("libfdx_desktop_native_build_debug")
}

tasks.register("plugin_test_desktop_native_vulkan_release_build") {
    group = "application"
    description = "Builds the plugin-use desktop_native Vulkan graphics test Release executable."
    dependsOn("libfdx_desktop_native_build_release")
}

tasks.register("plugin_test_desktop_native_vulkan_debug_run") {
    group = "application"
    description = "Runs the plugin-use desktop_native Vulkan graphics test Debug executable."
    dependsOn("libfdx_desktop_native_run_debug")
}

tasks.register("plugin_test_desktop_native_vulkan_release_run") {
    group = "application"
    description = "Runs the plugin-use desktop_native Vulkan graphics test Release executable."
    dependsOn("libfdx_desktop_native_run_release")
}

fun registerPspAlias(generateTask: String, buildTask: String, captureTask: String, label: String) {
    tasks.register(generateTask) {
        group = "application"
        description = "Generates the plugin-use libfdx PSP $label TeaVM C project."
        dependsOn("libfdx_psp_generate")
    }
    tasks.register(buildTask) {
        group = "application"
        description = "Generates and builds the plugin-use libfdx PSP $label EBOOT project."
        dependsOn("libfdx_psp_build")
    }
    tasks.register(captureTask) {
        group = "application"
        description = "Builds the plugin-use libfdx PSP $label and captures a PPSSPP emulator frame."
        dependsOn("libfdx_psp_ppsspp_capture")
    }
}

registerPspAlias("plugin_test_cube_generate", "plugin_test_cube_build",
        "plugin_test_cube_ppsspp_capture", "cube test")
registerPspAlias("plugin_test_spritebatch_generate", "plugin_test_spritebatch_build",
        "plugin_test_spritebatch_ppsspp_capture", "SpriteBatch test")
registerPspAlias("plugin_test_backend_clear_generate", "plugin_test_backend_clear_build",
        "plugin_test_backend_clear_ppsspp_capture", "ApplicationBackend clear-only test")
registerPspAlias("plugin_test_backend_shape_generate", "plugin_test_backend_shape_build",
        "plugin_test_backend_shape_ppsspp_capture", "ApplicationBackend shape-only test")
registerPspAlias("plugin_test_backend_ui_panel_generate",
        "plugin_test_backend_ui_panel_build", "plugin_test_backend_ui_panel_ppsspp_capture",
        "ApplicationBackend UIKit panel-only test")
registerPspAlias("plugin_test_backend_spritebatch_generate",
        "plugin_test_backend_spritebatch_build", "plugin_test_backend_spritebatch_ppsspp_capture",
        "ApplicationBackend asset SpriteBatch test")
registerPspAlias("plugin_test_backend_input_generate", "plugin_test_backend_input_build",
        "plugin_test_backend_input_ppsspp_capture", "ApplicationBackend input test")
registerPspAlias("plugin_test_backend_uikit_generate", "plugin_test_backend_uikit_build",
        "plugin_test_backend_uikit_ppsspp_capture", "ApplicationBackend UIKit test")
registerPspAlias("plugin_test_backend_uikit_smoke_generate",
        "plugin_test_backend_uikit_smoke_build", "plugin_test_backend_uikit_smoke_ppsspp_capture",
        "scripted ApplicationBackend UIKit smoke test")

fun Task.configureWebGpuPage(webappPath: String, title: String) {
    val webappDir = layout.buildDirectory.dir(webappPath)
    val indexFile = webappDir.map { it.file("index.html") }
    val outputFile = webappDir.map { it.file("webgpu.html") }
    inputs.file(indexFile)
    outputs.file(outputFile)
    doLast {
        writeWebGpuPage(indexFile.get().asFile, outputFile.get().asFile, title)
    }
}

fun writeWebGpuPage(indexFile: File, outputFile: File, title: String) {
    val source = indexFile.readText()
    val withTitle = source.replace(Regex("<title>.*</title>"), "<title>$title</title>")
    val rewritten = when {
        withTitle.contains("main();") -> withTitle.replace(
            "main();",
            "main([\"--graphics=webgpu\"]);"
        )
        withTitle.contains("teavm.exports.main([]);") -> withTitle.replace(
            "teavm.exports.main([]);",
            "teavm.exports.main([\"--graphics=webgpu\"]);"
        )
        else -> throw GradleException("Could not create WebGPU launch page from ${indexFile.absolutePath}")
    }
    outputFile.writeText(rewritten)
}
