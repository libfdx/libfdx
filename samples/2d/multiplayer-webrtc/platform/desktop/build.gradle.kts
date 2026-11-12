import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
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
    archivesName.set("sample_multiplayer_2d_webrtc_desktop")
}

val sampleProjectPath = project.path.substringBefore(":platform:")
val sampleRoot = layout.projectDirectory.dir("../..")

dependencies {
    implementation(project("$sampleProjectPath:core"))
    implementation(project(":libfdx:framework:application"))
    implementation(project(":libfdx:framework:display"))
    implementation(project(":libfdx:backends:desktop"))
    implementation(project(":libfdx:extensions:net:webrtc:platform:desktop_jni"))
    implementation(project(":libfdx:extensions:graphics:d3d12:core"))
    implementation(project(":libfdx:extensions:graphics:wgpu:core"))
    glRuntimeClasspath(project(":libfdx:extensions:graphics:gl:platform:desktop"))
    vulkanRuntimeClasspath(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
    wgpuRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
}

libfdx {
    desktopJvm {
        mainClass.set("io.github.libfdx.samples.multiplayer.webrtc.desktop.MultiplayerWebRtcDesktopLauncher")
        workingDir.set(sampleRoot)
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
            runDescription.set("Runs the WebRTC multiplayer 2D desktop sample with GL.")
        }
        target("wgpu") {
            displayName.set("WGPU")
            runtimeClasspath(wgpuRuntimeClasspath)
            systemProperty("libfdx.sample.graphics", "wgpu")
            systemProperty("libfdx.sample.graphicsLabel", "WGPU")
            runDescription.set("Runs the WebRTC multiplayer 2D desktop sample with WGPU.")
        }
        target("vulkan") {
            displayName.set("Vulkan")
            runtimeClasspath(vulkanRuntimeClasspath)
            systemProperty("libfdx.sample.graphics", "vulkan")
            systemProperty("libfdx.sample.graphicsLabel", "Vulkan")
            runDescription.set("Runs the WebRTC multiplayer 2D desktop sample with Vulkan.")
        }
        target("d3d12") {
            displayName.set("Direct3D 12")
            systemProperty("libfdx.sample.graphics", "d3d12")
            systemProperty("libfdx.sample.graphicsLabel", "Direct3D 12")
            runDescription.set("Runs the WebRTC multiplayer 2D desktop sample with Direct3D 12 on Windows.")
        }
    }
}
