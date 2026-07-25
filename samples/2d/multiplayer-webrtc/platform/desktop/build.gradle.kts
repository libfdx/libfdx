import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("java")
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
    archivesName.set("sample_multiplayer_2d_webrtc_desktop")
}

dependencies {
    implementation(project(":samples:2d:multiplayer-webrtc:core"))
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:application:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:display:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:backend_desktop:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:webrtc_desktop_jni:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:d3d12_core:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:wgpu_core:${libs.versions.libfdxSnapshot.get()}")
        glRuntimeClasspath("${libs.versions.libfdxGroup.get()}:gl_desktop:${libs.versions.libfdxSnapshot.get()}")
        vulkanRuntimeClasspath("${libs.versions.libfdxGroup.get()}:vulkan_desktop:${libs.versions.libfdxSnapshot.get()}")
        wgpuRuntimeClasspath("${libs.versions.libfdxGroup.get()}:wgpu_desktop_ffm:${libs.versions.libfdxSnapshot.get()}")
    } else {
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
}

val sampleMainClass = "io.github.libfdx.samples.multiplayer.webrtc.desktop.MultiplayerWebRtcDesktopLauncher"

fun JavaExec.configureMultiplayerRun(graphics: String, label: String) {
    group = "application"
    classpath = sourceSets["main"].runtimeClasspath + when (graphics) {
        "gl" -> glRuntimeClasspath
        "vulkan" -> vulkanRuntimeClasspath
        "d3d12" -> files()
        else -> wgpuRuntimeClasspath
    }
    mainClass.set(sampleMainClass)
    workingDir = rootProject.projectDir
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    jvmArgs("-Dorg.lwjgl.system.stackSize=1024", "--enable-native-access=ALL-UNNAMED")
    systemProperty("libfdx.sample.graphics", graphics)
    systemProperty("libfdx.sample.graphicsLabel", label)
    System.getProperty("libfdx.sample.signalingUrl")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("libfdx.sample.signalingUrl", it)
    }
    System.getProperty("libfdx.sample.autoHost")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("libfdx.sample.autoHost", it)
    }
    System.getProperty("libfdx.sample.autoJoinRoom")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("libfdx.sample.autoJoinRoom", it)
    }
    System.getProperty("libfdx.sample.playerName")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("libfdx.sample.playerName", it)
    }
    System.getProperty("libfdx.sample.hostRoomId")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("libfdx.sample.hostRoomId", it)
    }
    System.getProperty("libfdx.sample.exitAfterFrames")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("libfdx.sample.exitAfterFrames", it)
    }
    System.getProperty("libfdx.sample.validate")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("libfdx.sample.validate", it)
    }
    System.getProperty("libfdx.validation.scenario")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("libfdx.validation.scenario", it)
    }
}

tasks.register<JavaExec>("multiplayer_2d_webrtc_desktop_gl_run") {
    description = "Runs the WebRTC multiplayer 2D desktop sample with GL."
    configureMultiplayerRun("gl", "GL")
}

tasks.register<JavaExec>("multiplayer_2d_webrtc_desktop_wgpu_run") {
    description = "Runs the WebRTC multiplayer 2D desktop sample with WGPU."
    configureMultiplayerRun("wgpu", "WGPU")
}

tasks.register<JavaExec>("multiplayer_2d_webrtc_desktop_vulkan_run") {
    description = "Runs the WebRTC multiplayer 2D desktop sample with Vulkan."
    configureMultiplayerRun("vulkan", "Vulkan")
}

tasks.register<JavaExec>("multiplayer_2d_webrtc_desktop_d3d12_run") {
    description = "Runs the WebRTC multiplayer 2D desktop sample with Direct3D 12 on Windows."
    configureMultiplayerRun("d3d12", "Direct3D 12")
}
