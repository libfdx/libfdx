import io.github.libfdx.build.LibExt

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

dependencies {
    implementation(project(":tests:platform:desktop"))
    implementation(project(":tests:platform:web"))
    implementation(project(":tests:platform:desktop_c"))
    implementation(project(":tests:platform:psp"))

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
        mainClass.set("io.github.libfdx.tests.desktop.DesktopTestLauncher")
        forwardSystemPropertyPrefix("libfdx.test.")
        forwardSystemPropertyPrefix("libfdx.validation.")

        target("gl") {
            displayName.set("GL")
            systemProperty("libfdx.test.graphics", "gl")
            systemProperty("libfdx.test.graphicsLabel", "GL")
            launchProperty("graphics", "gl")
            launchProperty("graphicsLabel", "GL")
            buildDescription.set("Builds the plugin-use desktop graphics test GL release jar.")
            runDescription.set("Runs the plugin-use desktop graphics test with GL.")
        }

        target("wgpu") {
            displayName.set("WGPU")
            systemProperty("libfdx.test.graphics", "wgpu")
            systemProperty("libfdx.test.graphicsLabel", "WGPU")
            launchProperty("graphics", "wgpu")
            launchProperty("graphicsLabel", "WGPU")
            buildDescription.set("Builds the plugin-use desktop graphics test WGPU release jar.")
            runDescription.set("Runs the plugin-use desktop graphics test with WGPU.")
        }

        target("vulkan") {
            displayName.set("Vulkan")
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

        target("webgl") {
            buildDescription.set("Builds the plugin-use WebGL JavaScript test web application.")
            runDescription.set("Builds and serves the plugin-use WebGL JavaScript test web application.")
        }

        target("webgpu") {
            defaultPath.set("/?graphics=webgpu")
            buildDescription.set("Builds the plugin-use WebGPU JavaScript test web application.")
            runDescription.set("Builds and serves the plugin-use WebGPU JavaScript test web application.")
        }
    }

    wasm {
        mainClass.set("io.github.libfdx.tests.web.WebTestWasmLauncher")
        htmlTitle.set("libfdx Plugin Tests - WebGL Wasm")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)

        target("webgl") {
            buildDescription.set("Builds the plugin-use WebGL Wasm test web application.")
            runDescription.set("Builds and serves the plugin-use WebGL Wasm test web application.")
        }

        target("webgpu") {
            defaultPath.set("/?graphics=webgpu")
            buildDescription.set("Builds the plugin-use WebGPU Wasm test web application.")
            runDescription.set("Builds and serves the plugin-use WebGPU Wasm test web application.")
        }
    }

    desktopC {
        minHeapSize.set(64)
        maxHeapSize.set(1024)

        target("opengl") {
            displayName.set("plugin-use desktop_c OpenGL graphics test")
            mainClass.set("io.github.libfdx.tests.desktopc.DesktopCOpenGLTestLauncher")
            targetFileName.set("libfdx-tests-plugin-opengl-desktop-c")
        }

        target("vulkan") {
            displayName.set("plugin-use desktop_c Vulkan graphics test")
            mainClass.set("io.github.libfdx.tests.desktopc.DesktopCVulkanTestLauncher")
            targetFileName.set("libfdx-tests-plugin-vulkan-desktop-c")
        }
    }

    psp {
        optimization.set(OptimizationLevel.BALANCED)
        debugInformation.set(true)
        debugMemory.set(false)
        maxHeapSize.set(32)

        target("test") {
            displayName.set("plugin-use libfdx PSP shared test selector")
            mainClass.set("io.github.libfdx.tests.psp.PspTestSelectorLauncher")
            targetFileName.set("libfdx-plugin-tests-psp")
        }
    }
}
