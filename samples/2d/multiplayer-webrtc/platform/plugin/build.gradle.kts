import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    id("java")
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

val glRuntimeClasspath = configurations.create("glRuntimeClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val vulkanRuntimeClasspath = configurations.create("vulkanRuntimeClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val wgpuRuntimeClasspath = configurations.create("wgpuRuntimeClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

base {
    archivesName.set("sample_multiplayer_2d_webrtc_plugin")
}

val sampleProjectPath = project.path.substringBefore(":platform:")
val desktopProject = findProject("$sampleProjectPath:platform:desktop")
val webProject = findProject("$sampleProjectPath:platform:web")
val libfdxDependencyVersion =
    gradle.extensions.extraProperties.get("libfdxDependencyVersion") as String

dependencies {
    desktopProject?.let { implementation(it) }
    webProject?.let { implementation(it) }

    if (desktopProject != null) {
        if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
            glRuntimeClasspath("${libs.versions.libfdxGroup.get()}:gl_desktop:$libfdxDependencyVersion")
            vulkanRuntimeClasspath("${libs.versions.libfdxGroup.get()}:vulkan_desktop:$libfdxDependencyVersion")
            wgpuRuntimeClasspath("${libs.versions.libfdxGroup.get()}:wgpu_desktop_ffm:$libfdxDependencyVersion")
        } else {
            glRuntimeClasspath(project(":libfdx:extensions:graphics:gl:platform:desktop"))
            vulkanRuntimeClasspath(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
            wgpuRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
        }
    }
}

libfdx {
    if (desktopProject != null) {
        desktopJvm {
            mainClass.set("io.github.libfdx.samples.multiplayer.webrtc.desktop.MultiplayerWebRtcDesktopLauncher")
            forwardSystemProperty("libfdx.sample.signalingUrl")
            forwardSystemProperty("libfdx.sample.autoHost")
            forwardSystemProperty("libfdx.sample.autoJoinRoom")
            forwardSystemProperty("libfdx.sample.playerName")
            forwardSystemProperty("libfdx.sample.hostRoomId")
            forwardSystemProperty("libfdx.sample.exitAfterFrames")
            forwardSystemProperty("libfdx.sample.validate")
            forwardSystemProperty("libfdx.validation.scenario")

            target("gl") {
                displayName.set("GL")
                runtimeClasspath(glRuntimeClasspath)
                systemProperty("libfdx.sample.graphics", "gl")
                systemProperty("libfdx.sample.graphicsLabel", "GL")
            }
            target("wgpu") {
                displayName.set("WGPU")
                runtimeClasspath(wgpuRuntimeClasspath)
                systemProperty("libfdx.sample.graphics", "wgpu")
                systemProperty("libfdx.sample.graphicsLabel", "WGPU")
            }
            target("vulkan") {
                displayName.set("Vulkan")
                runtimeClasspath(vulkanRuntimeClasspath)
                systemProperty("libfdx.sample.graphics", "vulkan")
                systemProperty("libfdx.sample.graphicsLabel", "Vulkan")
            }
            target("d3d12") {
                displayName.set("Direct3D 12")
                systemProperty("libfdx.sample.graphics", "d3d12")
                systemProperty("libfdx.sample.graphicsLabel", "Direct3D 12")
            }
        }
    }

    if (webProject != null) {
        js {
            mainClass.set("io.github.libfdx.samples.multiplayer.webrtc.web.MultiplayerWebRtcWebJsLauncher")
            htmlTitle.set("libfdx WebRTC Multiplayer 2D")
            canvasId.set("libfdx-canvas")
            htmlWidth.set(0)
            htmlHeight.set(0)

            target("webgl") {
                defaultPath.set("/?graphics=webgl")
            }
            target("webgpu") {
                defaultPath.set("/?graphics=webgpu")
            }
        }

        wasm {
            mainClass.set("io.github.libfdx.samples.multiplayer.webrtc.web.MultiplayerWebRtcWebWasmLauncher")
            htmlTitle.set("libfdx WebRTC Multiplayer 2D")
            canvasId.set("libfdx-canvas")
            htmlWidth.set(0)
            htmlHeight.set(0)

            target("webgl") {
                defaultPath.set("/?graphics=webgl")
            }
        }
    }
}
