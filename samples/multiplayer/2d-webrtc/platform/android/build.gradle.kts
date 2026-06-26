import io.github.libfdx.build.LibExt

plugins {
    alias(libs.plugins.android.application)
}

val androidCompileSdkVersion = providers.gradleProperty("androidCompileSdk").get().toInt()
val androidMinSdkVersion = providers.gradleProperty("androidMinSdk").get().toInt()
val androidTargetSdkVersion = providers.gradleProperty("androidTargetSdk").get().toInt()

group = "${LibExt.fdxGroup}.samples.multiplayer"

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
        sourceCompatibility = JavaVersion.toVersion(25)
        targetCompatibility = JavaVersion.toVersion(25)
    }
}

dependencies {
    implementation(project(":samples:multiplayer:2d-webrtc:core"))
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_android:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:wgpu_android_jni:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:vulkan_android_jni:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:webrtc_android_jni:${LibExt.publishedLibfdxVersion}")
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

fun androidStartCommand(activity: String): List<String> {
    val command = mutableListOf(
            android.adbExecutable.absolutePath,
            "shell",
            "am",
            "start",
            "-n",
            "io.github.libfdx.samples.multiplayer.webrtc.android/$activity")
    fun stringExtra(name: String) {
        System.getProperty(name)?.takeIf { it.isNotBlank() }?.let {
            command.add("--es")
            command.add(name)
            command.add(it)
        }
    }
    fun booleanExtra(name: String) {
        System.getProperty(name)?.takeIf { it.isNotBlank() }?.let {
            command.add("--ez")
            command.add(name)
            command.add(it)
        }
    }
    stringExtra("libfdx.sample.signalingUrl")
    stringExtra("libfdx.sample.playerName")
    stringExtra("libfdx.sample.hostRoomId")
    stringExtra("libfdx.sample.autoJoinRoom")
    stringExtra("libfdx.sample.exitAfterFrames")
    stringExtra("libfdx.validation.scenario")
    booleanExtra("libfdx.sample.autoHost")
    booleanExtra("libfdx.sample.validate")
    return command
}

tasks.register<Exec>("multiplayer_2d_webrtc_android_wgpu_jni_run") {
    group = "application"
    description = "Installs and launches the Android WGPU JNI WebRTC multiplayer 2D sample."
    dependsOn("installDebug")
    commandLine(androidStartCommand(
            "io.github.libfdx.samples.multiplayer.webrtc.android.MultiplayerWebRtcAndroidWgpuActivity"))
}

tasks.register<Exec>("multiplayer_2d_webrtc_android_vulkan_run") {
    group = "application"
    description = "Installs and launches the Android Vulkan WebRTC multiplayer 2D sample."
    dependsOn("installDebug")
    commandLine(androidStartCommand(
            "io.github.libfdx.samples.multiplayer.webrtc.android.MultiplayerWebRtcAndroidVulkanActivity"))
}
