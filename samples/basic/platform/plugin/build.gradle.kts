import io.github.libfdx.build.LibExt

import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    id("java")
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.samples.basic"

base {
    archivesName.set("sample_basic_plugin")
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
    implementation(project(":samples:basic:platform:desktop"))
    implementation(project(":samples:basic:platform:desktop_native"))
    implementation(project(":samples:basic:platform:web"))
    desktopApplicationRuntimeClasspath(project(":samples:basic:platform:desktop"))

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

libfdx {
    desktopJvm {
        taskNamePrefix.set("plugin_basic_desktop")
        mainClass.set("io.github.libfdx.samples.basic.desktop.BasicDesktopLauncher")
        runtimeClasspath(desktopApplicationRuntimeClasspath)
        forwardSystemProperty("libfdx.sample.exitAfterFrames")
        provider("gl") {
            displayName.set("GL")
            runtimeClasspath(glRuntimeClasspath)
            systemProperty("libfdx.sample.graphics", "gl")
            systemProperty("libfdx.sample.graphicsLabel", "GL")
            launchProperty("graphics", "gl")
            launchProperty("graphicsLabel", "GL")
            buildDescription.set("Builds the plugin-use basic desktop GL release jar.")
            runDescription.set("Runs the plugin-use basic desktop sample with GL.")
        }
        provider("wgpu") {
            displayName.set("WGPU")
            runtimeClasspath(wgpuRuntimeClasspath)
            systemProperty("libfdx.sample.graphics", "wgpu")
            systemProperty("libfdx.sample.graphicsLabel", "WGPU")
            launchProperty("graphics", "wgpu")
            launchProperty("graphicsLabel", "WGPU")
            buildDescription.set("Builds the plugin-use basic desktop WGPU release jar.")
            runDescription.set("Runs the plugin-use basic desktop sample with WGPU.")
        }
        provider("vulkan") {
            displayName.set("Vulkan")
            runtimeClasspath(vulkanRuntimeClasspath)
            systemProperty("libfdx.sample.graphics", "vulkan")
            systemProperty("libfdx.sample.graphicsLabel", "Vulkan")
            launchProperty("graphics", "vulkan")
            launchProperty("graphicsLabel", "Vulkan")
            buildDescription.set("Builds the plugin-use basic desktop Vulkan release jar.")
            runDescription.set("Runs the plugin-use basic desktop sample with Vulkan.")
        }
    }
    js {
        mainClass.set("io.github.libfdx.samples.basic.web.BasicWebJsLauncher")
        htmlTitle.set("libfdx Plugin Basic - WebGL JS")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)
    }
    wasm {
        mainClass.set("io.github.libfdx.samples.basic.web.BasicWebWasmLauncher")
        htmlTitle.set("libfdx Plugin Basic - WebGL Wasm")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)
    }
    desktopNative {
        mainClass.set("io.github.libfdx.samples.basic.desktopnative.BasicDesktopNativeLauncher")
        targetFileName.set("libfdx-basic-gl-plugin-desktop-native")
        showConsole.set(providers.gradleProperty("libfdx.desktopNative.showConsole")
                .map { value -> value.toBooleanStrictOrNull() ?: value.toBoolean() }
                .orElse(true))
    }
}

tasks.register("plugin_basic_webgl_js_build") {
    group = "application"
    description = "Builds the plugin-use basic WebGL JavaScript web application."
    dependsOn("libfdx_web_js_build")
}

tasks.register("plugin_basic_webgl_wasm_build") {
    group = "application"
    description = "Builds the plugin-use basic WebGL Wasm web application."
    dependsOn("libfdx_web_wasm_build")
}

tasks.register("plugin_basic_webgpu_js_build") {
    group = "application"
    description = "Builds the plugin-use basic WebGPU JavaScript web application."
    dependsOn("libfdx_web_js_build")
    configureWebGpuPage("dist/web-js/webapp", "libfdx Plugin Basic - WebGPU JS")
}

tasks.register("plugin_basic_webgpu_wasm_build") {
    group = "application"
    description = "Builds the plugin-use basic WebGPU Wasm web application."
    dependsOn("libfdx_web_wasm_build")
    configureWebGpuPage("dist/web-wasm/webapp", "libfdx Plugin Basic - WebGPU Wasm")
}

tasks.register("plugin_basic_desktop_native_gl_debug_generate") {
    group = "application"
    description = "Generates the plugin-use basic desktop_native GL sample Debug project."
    dependsOn("libfdx_desktop_native_generate")
}

tasks.register("plugin_basic_desktop_native_gl_release_generate") {
    group = "application"
    description = "Generates the plugin-use basic desktop_native GL sample Release project."
    dependsOn("libfdx_desktop_native_generate")
}

tasks.register("plugin_basic_desktop_native_gl_debug_build") {
    group = "application"
    description = "Builds the plugin-use basic desktop_native GL sample Debug executable."
    dependsOn("libfdx_desktop_native_build_debug")
}

tasks.register("plugin_basic_desktop_native_gl_release_build") {
    group = "application"
    description = "Builds the plugin-use basic desktop_native GL sample Release executable."
    dependsOn("libfdx_desktop_native_build_release")
}

tasks.register("plugin_basic_desktop_native_gl_debug_run") {
    group = "application"
    description = "Runs the plugin-use basic desktop_native GL sample Debug executable."
    dependsOn("libfdx_desktop_native_run_debug")
}

tasks.register("plugin_basic_desktop_native_gl_release_run") {
    group = "application"
    description = "Runs the plugin-use basic desktop_native GL sample Release executable."
    dependsOn("libfdx_desktop_native_run_release")
}

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
