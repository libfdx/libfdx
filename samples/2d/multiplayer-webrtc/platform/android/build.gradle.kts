
plugins {
    alias(libs.plugins.android.application)
    id("io.github.libfdx")
}

val sampleProjectPath = project.path.substringBefore(":platform:")
val libfdxDependencyVersion =
    gradle.extensions.extraProperties.get("libfdxDependencyVersion") as String
val androidCompileSdkVersion = providers.gradleProperty("androidCompileSdk").get().toInt()
val androidMinSdkVersion = providers.gradleProperty("androidMinSdk").get().toInt()
val androidTargetSdkVersion = providers.gradleProperty("androidTargetSdk").get().toInt()


android {
    namespace = "io.github.libfdx.samples.multiplayer.webrtc.android"
    compileSdk = androidCompileSdkVersion

    defaultConfig {
        applicationId = "io.github.libfdx.samples.multiplayer.webrtc.android"
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project("$sampleProjectPath:core"))
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_android:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:wgpu_android_jni:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:vulkan_android_jni:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:webrtc_android_jni:$libfdxDependencyVersion")
    } else {
        implementation(project(":libfdx:backends:android"))
        implementation(project(":libfdx:extensions:graphics:wgpu:platform:android_jni"))
        implementation(project(":libfdx:extensions:graphics:vulkan:platform:android_jni"))
        implementation(project(":libfdx:extensions:net:webrtc:platform:android_jni"))
    }
}

base {
    archivesName.set("sample_multiplayer_2d_webrtc_android")
}

libfdx {
    android {
        applicationId.set("io.github.libfdx.samples.multiplayer.webrtc.android")
        adbExecutable.set(androidComponents.sdkComponents.adb)
        forwardStringSystemProperty("libfdx.sample.signalingUrl")
        forwardStringSystemProperty("libfdx.sample.playerName")
        forwardStringSystemProperty("libfdx.sample.hostRoomId")
        forwardStringSystemProperty("libfdx.sample.autoJoinRoom")
        forwardStringSystemProperty("libfdx.sample.exitAfterFrames")
        forwardStringSystemProperty("libfdx.validation.scenario")
        forwardBooleanSystemProperty("libfdx.sample.autoHost")
        forwardBooleanSystemProperty("libfdx.sample.validate")

        target("wgpu_jni") {
            displayName.set("WebRTC multiplayer 2D WGPU JNI sample")
            activity.set(
                "io.github.libfdx.samples.multiplayer.webrtc.android.MultiplayerWebRtcAndroidWgpuActivity"
            )
            runDescription.set("Installs and launches the Android WGPU JNI WebRTC multiplayer 2D sample.")
        }
        target("vulkan") {
            displayName.set("WebRTC multiplayer 2D Vulkan sample")
            activity.set(
                "io.github.libfdx.samples.multiplayer.webrtc.android.MultiplayerWebRtcAndroidVulkanActivity"
            )
            runDescription.set("Installs and launches the Android Vulkan WebRTC multiplayer 2D sample.")
        }
    }
}
