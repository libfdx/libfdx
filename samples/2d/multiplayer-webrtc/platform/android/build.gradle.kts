
plugins {
    alias(libs.plugins.android.application)
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

val adbExecutable = androidComponents.sdkComponents.adb

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

fun androidStartCommand(activity: String): List<String> {
    val command = mutableListOf(
            adbExecutable.get().asFile.absolutePath,
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
    doFirst {
        commandLine(androidStartCommand(
                "io.github.libfdx.samples.multiplayer.webrtc.android.MultiplayerWebRtcAndroidWgpuActivity"))
    }
}

tasks.register<Exec>("multiplayer_2d_webrtc_android_vulkan_run") {
    group = "application"
    description = "Installs and launches the Android Vulkan WebRTC multiplayer 2D sample."
    dependsOn("installDebug")
    doFirst {
        commandLine(androidStartCommand(
                "io.github.libfdx.samples.multiplayer.webrtc.android.MultiplayerWebRtcAndroidVulkanActivity"))
    }
}
