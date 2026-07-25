
import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    id("java")
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
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

base {
    archivesName.set("sample_2d_sprite_movement_plugin")
}

dependencies {
    implementation(project(":samples:2d:sprite-movement:platform:desktop"))
    implementation(project(":samples:2d:sprite-movement:platform:desktop_c"))
    implementation(project(":samples:2d:sprite-movement:platform:ios_c"))
    implementation(project(":samples:2d:sprite-movement:platform:web"))

    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        glRuntimeClasspath("${libs.versions.libfdxGroup.get()}:gl_desktop:${libs.versions.libfdxSnapshot.get()}")
        vulkanRuntimeClasspath("${libs.versions.libfdxGroup.get()}:vulkan_desktop:${libs.versions.libfdxSnapshot.get()}")
        wgpuRuntimeClasspath("${libs.versions.libfdxGroup.get()}:wgpu_desktop_ffm:${libs.versions.libfdxSnapshot.get()}")
    } else {
        glRuntimeClasspath(project(":libfdx:extensions:graphics:gl:platform:desktop"))
        vulkanRuntimeClasspath(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
        wgpuRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
    }
}

libfdx {
    assets(
        rootProject.file("samples/2d/sprite-movement/assets"),
        rootProject.file("samples/2d/sprite-movement/scenes")
    )
    desktopJvm {
        mainClass.set("io.github.libfdx.samples.g2d.spritemovement.desktop.SpriteMovementDesktopLauncher")
        workingDir.set(rootProject.layout.projectDirectory.dir("samples/2d/sprite-movement"))
        forwardSystemProperty("libfdx.sample.exitAfterFrames")
        forwardSystemProperty("libfdx.sample.maximized")
        target("gl") {
            displayName.set("GL")
            runtimeClasspath(glRuntimeClasspath)
            systemProperty("libfdx.sample.graphics", "gl")
            systemProperty("libfdx.sample.graphicsLabel", "GL")
            launchProperty("graphics", "gl")
            launchProperty("graphicsLabel", "GL")
            buildDescription.set("Builds the plugin-use 2D Sprite Movement desktop GL release jar.")
            runDescription.set("Runs the plugin-use 2D Sprite Movement desktop sample with GL.")
        }
        target("wgpu") {
            displayName.set("WGPU")
            runtimeClasspath(wgpuRuntimeClasspath)
            systemProperty("libfdx.sample.graphics", "wgpu")
            systemProperty("libfdx.sample.graphicsLabel", "WGPU")
            launchProperty("graphics", "wgpu")
            launchProperty("graphicsLabel", "WGPU")
            buildDescription.set("Builds the plugin-use 2D Sprite Movement desktop WGPU release jar.")
            runDescription.set("Runs the plugin-use 2D Sprite Movement desktop sample with WGPU.")
        }
        target("vulkan") {
            displayName.set("Vulkan")
            runtimeClasspath(vulkanRuntimeClasspath)
            systemProperty("libfdx.sample.graphics", "vulkan")
            systemProperty("libfdx.sample.graphicsLabel", "Vulkan")
            launchProperty("graphics", "vulkan")
            launchProperty("graphicsLabel", "Vulkan")
            buildDescription.set("Builds the plugin-use 2D Sprite Movement desktop Vulkan release jar.")
            runDescription.set("Runs the plugin-use 2D Sprite Movement desktop sample with Vulkan.")
        }
        target("d3d12") {
            displayName.set("Direct3D 12")
            systemProperty("libfdx.sample.graphics", "d3d12")
            systemProperty("libfdx.sample.graphicsLabel", "Direct3D 12")
            launchProperty("graphics", "d3d12")
            launchProperty("graphicsLabel", "Direct3D 12")
            buildDescription.set("Builds the plugin-use 2D Sprite Movement desktop Direct3D 12 release jar.")
            runDescription.set("Runs the plugin-use 2D Sprite Movement desktop sample with Direct3D 12 on Windows.")
        }
    }
    js {
        mainClass.set("io.github.libfdx.samples.g2d.spritemovement.web.SpriteMovementWebJsLauncher")
        htmlTitle.set("libfdx Plugin 2D Sprite Movement - WebGL JS")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)

        target("webgl") {
            buildDescription.set("Builds the plugin-use 2D Sprite Movement WebGL JavaScript web application.")
            runDescription.set("Builds and serves the plugin-use 2D Sprite Movement WebGL JavaScript web application.")
        }

        target("webgpu") {
            defaultPath.set("/?graphics=webgpu")
            buildDescription.set("Builds the plugin-use 2D Sprite Movement WebGPU JavaScript web application.")
            runDescription.set("Builds and serves the plugin-use 2D Sprite Movement WebGPU JavaScript web application.")
        }
    }
    wasm {
        mainClass.set("io.github.libfdx.samples.g2d.spritemovement.web.SpriteMovementWebWasmLauncher")
        htmlTitle.set("libfdx Plugin 2D Sprite Movement - WebGL Wasm")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)

        target("webgl") {
            buildDescription.set("Builds the plugin-use 2D Sprite Movement WebGL Wasm web application.")
            runDescription.set("Builds and serves the plugin-use 2D Sprite Movement WebGL Wasm web application.")
        }
    }
    desktopC {
        showConsole.set(providers.gradleProperty("libfdx.desktopC.showConsole")
                .map { value -> value.toBooleanStrictOrNull() ?: value.toBoolean() }
                .orElse(true))

        target("opengl") {
            displayName.set("plugin-use 2D Sprite Movement desktop_c GL sample")
            mainClass.set("io.github.libfdx.samples.g2d.spritemovement.desktopc.SpriteMovementDesktopCLauncher")
            targetFileName.set("libfdx-sprite-movement-gl-plugin-desktop-c")
        }
    }
    iosC {
        bundleIdentifier.set("io.github.libfdx.samples.g2d.spritemovement.iosc")

        target("gles") {
            displayName.set("plugin-use 2D Sprite Movement iOS C GLES sample")
            mainClass.set("io.github.libfdx.samples.g2d.spritemovement.iosc.SpriteMovementIosCLauncher")
            targetFileName.set("libfdx-sprite-movement-gles-ios-c")
            graphicsApi.set("gles")
        }
        target("metal") {
            displayName.set("plugin-use 2D Sprite Movement iOS C Metal sample")
            mainClass.set("io.github.libfdx.samples.g2d.spritemovement.iosc.SpriteMovementIosCMetalLauncher")
            targetFileName.set("libfdx-sprite-movement-metal-ios-c")
            graphicsApi.set("metal")
        }
    }
}
