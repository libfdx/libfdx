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
    archivesName.set("sample_basic_plugin")
}

dependencies {
    implementation(project(":samples:basic:platform:desktop"))
    implementation(project(":samples:basic:platform:desktop_c"))
    implementation(project(":samples:basic:platform:ios_c"))
    implementation(project(":samples:basic:platform:web"))

    if (LibExt.usePublishedLibfdx) {
        glRuntimeClasspath("${LibExt.fdxGroup}:gl_desktop:${LibExt.fdxSnapshotVersion}")
        vulkanRuntimeClasspath("${LibExt.fdxGroup}:vulkan_desktop:${LibExt.fdxSnapshotVersion}")
        wgpuRuntimeClasspath("${LibExt.fdxGroup}:wgpu_desktop_ffm:${LibExt.fdxSnapshotVersion}")
    } else {
        glRuntimeClasspath(project(":libfdx:extensions:graphics:gl:platform:desktop"))
        vulkanRuntimeClasspath(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
        wgpuRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
    }
}

libfdx {
    desktopJvm {
        mainClass.set("io.github.libfdx.samples.basic.desktop.BasicDesktopLauncher")
        forwardSystemProperty("libfdx.sample.exitAfterFrames")
        forwardSystemProperty("libfdx.sample.maximized")
        target("gl") {
            displayName.set("GL")
            runtimeClasspath(glRuntimeClasspath)
            systemProperty("libfdx.sample.graphics", "gl")
            systemProperty("libfdx.sample.graphicsLabel", "GL")
            launchProperty("graphics", "gl")
            launchProperty("graphicsLabel", "GL")
            buildDescription.set("Builds the plugin-use basic desktop GL release jar.")
            runDescription.set("Runs the plugin-use basic desktop sample with GL.")
        }
        target("wgpu") {
            displayName.set("WGPU")
            runtimeClasspath(wgpuRuntimeClasspath)
            systemProperty("libfdx.sample.graphics", "wgpu")
            systemProperty("libfdx.sample.graphicsLabel", "WGPU")
            launchProperty("graphics", "wgpu")
            launchProperty("graphicsLabel", "WGPU")
            buildDescription.set("Builds the plugin-use basic desktop WGPU release jar.")
            runDescription.set("Runs the plugin-use basic desktop sample with WGPU.")
        }
        target("vulkan") {
            displayName.set("Vulkan")
            runtimeClasspath(vulkanRuntimeClasspath)
            systemProperty("libfdx.sample.graphics", "vulkan")
            systemProperty("libfdx.sample.graphicsLabel", "Vulkan")
            launchProperty("graphics", "vulkan")
            launchProperty("graphicsLabel", "Vulkan")
            buildDescription.set("Builds the plugin-use basic desktop Vulkan release jar.")
            runDescription.set("Runs the plugin-use basic desktop sample with Vulkan.")
        }
        target("d3d12") {
            displayName.set("Direct3D 12")
            systemProperty("libfdx.sample.graphics", "d3d12")
            systemProperty("libfdx.sample.graphicsLabel", "Direct3D 12")
            launchProperty("graphics", "d3d12")
            launchProperty("graphicsLabel", "Direct3D 12")
            buildDescription.set("Builds the plugin-use basic desktop Direct3D 12 release jar.")
            runDescription.set("Runs the plugin-use basic desktop sample with Direct3D 12 on Windows.")
        }
    }
    js {
        mainClass.set("io.github.libfdx.samples.basic.web.BasicWebJsLauncher")
        htmlTitle.set("libfdx Plugin Basic - WebGL JS")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)

        target("webgl") {
            buildDescription.set("Builds the plugin-use basic WebGL JavaScript web application.")
            runDescription.set("Builds and serves the plugin-use basic WebGL JavaScript web application.")
        }

        target("webgpu") {
            defaultPath.set("/?graphics=webgpu")
            buildDescription.set("Builds the plugin-use basic WebGPU JavaScript web application.")
            runDescription.set("Builds and serves the plugin-use basic WebGPU JavaScript web application.")
        }
    }
    wasm {
        mainClass.set("io.github.libfdx.samples.basic.web.BasicWebWasmLauncher")
        htmlTitle.set("libfdx Plugin Basic - WebGL Wasm")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)

        target("webgl") {
            buildDescription.set("Builds the plugin-use basic WebGL Wasm web application.")
            runDescription.set("Builds and serves the plugin-use basic WebGL Wasm web application.")
        }
    }
    desktopC {
        showConsole.set(providers.gradleProperty("libfdx.desktopC.showConsole")
                .map { value -> value.toBooleanStrictOrNull() ?: value.toBoolean() }
                .orElse(true))

        target("opengl") {
            displayName.set("plugin-use basic desktop_c GL sample")
            mainClass.set("io.github.libfdx.samples.basic.desktopc.BasicDesktopCLauncher")
            targetFileName.set("libfdx-basic-gl-plugin-desktop-c")
        }
    }
    iosC {
        bundleIdentifier.set("io.github.libfdx.samples.basic.iosc")

        target("gles") {
            displayName.set("plugin-use basic iOS C GLES sample")
            mainClass.set("io.github.libfdx.samples.basic.iosc.BasicIosCLauncher")
            targetFileName.set("libfdx-basic-gles-ios-c")
            graphicsApi.set("gles")
        }
        target("metal") {
            displayName.set("plugin-use basic iOS C Metal sample")
            mainClass.set("io.github.libfdx.samples.basic.iosc.BasicIosCMetalLauncher")
            targetFileName.set("libfdx-basic-metal-ios-c")
            graphicsApi.set("metal")
        }
    }
}
