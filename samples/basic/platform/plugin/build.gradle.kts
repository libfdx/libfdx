import io.github.libfdx.build.LibExt

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

dependencies {
    implementation(project(":samples:basic:platform:desktop"))
    implementation(project(":samples:basic:platform:desktop_c"))
    implementation(project(":samples:basic:platform:ios_c"))
    implementation(project(":samples:basic:platform:web"))

    if (LibExt.usePublishedLibfdx) {
        runtimeOnly("${LibExt.fdxGroup}:gl_desktop:${LibExt.publishedLibfdxVersion}")
        runtimeOnly("${LibExt.fdxGroup}:vulkan_desktop:${LibExt.publishedLibfdxVersion}")
        runtimeOnly("${LibExt.fdxGroup}:wgpu_desktop_ffm:${LibExt.publishedLibfdxVersion}")
    } else {
        runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop"))
        runtimeOnly(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
        runtimeOnly(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
    }
}

libfdx {
    desktopJvm {
        mainClass.set("io.github.libfdx.samples.basic.desktop.BasicDesktopLauncher")
        forwardSystemProperty("libfdx.sample.exitAfterFrames")
        target("gl") {
            displayName.set("GL")
            systemProperty("libfdx.sample.graphics", "gl")
            systemProperty("libfdx.sample.graphicsLabel", "GL")
            launchProperty("graphics", "gl")
            launchProperty("graphicsLabel", "GL")
            buildDescription.set("Builds the plugin-use basic desktop GL release jar.")
            runDescription.set("Runs the plugin-use basic desktop sample with GL.")
        }
        target("wgpu") {
            displayName.set("WGPU")
            systemProperty("libfdx.sample.graphics", "wgpu")
            systemProperty("libfdx.sample.graphicsLabel", "WGPU")
            launchProperty("graphics", "wgpu")
            launchProperty("graphicsLabel", "WGPU")
            buildDescription.set("Builds the plugin-use basic desktop WGPU release jar.")
            runDescription.set("Runs the plugin-use basic desktop sample with WGPU.")
        }
        target("vulkan") {
            displayName.set("Vulkan")
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

        target("webgpu") {
            defaultPath.set("/?graphics=webgpu")
            buildDescription.set("Builds the plugin-use basic WebGPU Wasm web application.")
            runDescription.set("Builds and serves the plugin-use basic WebGPU Wasm web application.")
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
