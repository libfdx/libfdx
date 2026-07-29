
plugins {
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}


base {
    archivesName.set("sample_multiplayer_2d_webrtc_web")
}

val sampleProjectPath = project.path.substringBefore(":platform:")
val libfdxDependencyVersion =
    gradle.extensions.extraProperties.get("libfdxDependencyVersion") as String

dependencies {
    implementation(project("$sampleProjectPath:core"))
    implementation(libs.teavm.jso)
    implementation(libs.teavm.jso.apis)
    implementation(libs.teavm.jso.impl)
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_web:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:gl_web:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:wgpu_web:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:webrtc_web:$libfdxDependencyVersion")
    } else {
        implementation(project(":libfdx:backends:web"))
        implementation(project(":libfdx:extensions:graphics:gl:platform:web"))
        implementation(project(":libfdx:extensions:graphics:wgpu:platform:web"))
        implementation(project(":libfdx:extensions:net:webrtc:platform:web"))
    }
}

libfdx {
    js {
        mainClass.set("io.github.libfdx.samples.multiplayer.webrtc.web.MultiplayerWebRtcWebJsLauncher")
        htmlTitle.set("libfdx WebRTC Multiplayer 2D")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)

        target("webgl") {
            defaultPath.set("/?graphics=webgl")
            buildDescription.set("Builds the WebRTC multiplayer 2D WebGL JavaScript web application.")
            runDescription.set("Builds and serves the WebRTC multiplayer 2D WebGL JavaScript web application.")
        }
        target("webgpu") {
            defaultPath.set("/?graphics=webgpu")
            buildDescription.set("Builds the WebRTC multiplayer 2D WebGPU JavaScript web application.")
            runDescription.set("Builds and serves the WebRTC multiplayer 2D WebGPU JavaScript web application.")
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
            buildDescription.set("Builds the WebRTC multiplayer 2D WebGL Wasm web application.")
            runDescription.set("Builds and serves the WebRTC multiplayer 2D WebGL Wasm web application.")
        }
    }
}
