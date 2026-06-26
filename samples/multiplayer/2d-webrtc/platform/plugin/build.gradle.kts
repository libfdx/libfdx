import io.github.libfdx.build.LibExt

plugins {
    id("java")
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.samples.multiplayer"

base {
    archivesName.set("sample_multiplayer_2d_webrtc_plugin")
}

dependencies {
    implementation(project(":samples:multiplayer:2d-webrtc:platform:desktop"))
    implementation(project(":samples:multiplayer:2d-webrtc:platform:web"))

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
            systemProperty("libfdx.sample.graphics", "gl")
            systemProperty("libfdx.sample.graphicsLabel", "GL")
        }
        target("wgpu") {
            displayName.set("WGPU")
            systemProperty("libfdx.sample.graphics", "wgpu")
            systemProperty("libfdx.sample.graphicsLabel", "WGPU")
        }
        target("vulkan") {
            displayName.set("Vulkan")
            systemProperty("libfdx.sample.graphics", "vulkan")
            systemProperty("libfdx.sample.graphicsLabel", "Vulkan")
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
        target("webgpu") {
            defaultPath.set("/?graphics=webgpu")
        }
    }
}
