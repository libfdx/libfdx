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

group = "${LibExt.fdxGroup}.samples.multiplayer"

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
    archivesName.set("sample_multiplayer_2d_webrtc_plugin")
}

dependencies {
    implementation(project(":samples:2d:multiplayer-webrtc:platform:desktop"))
    implementation(project(":samples:2d:multiplayer-webrtc:platform:web"))

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
