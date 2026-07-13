import io.github.libfdx.build.LibExt

plugins {
    alias(libs.plugins.android.application)
}

val androidCompileSdkVersion = providers.gradleProperty("androidCompileSdk").get().toInt()
val androidMinSdkVersion = providers.gradleProperty("androidMinSdk").get().toInt()
val androidTargetSdkVersion = providers.gradleProperty("androidTargetSdk").get().toInt()

group = "${LibExt.fdxGroup}.samples.basic"

android {
    namespace = "io.github.libfdx.samples.basic.android"
    compileSdk = androidCompileSdkVersion

    defaultConfig {
        applicationId = "io.github.libfdx.samples.basic.android"
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            assets.directories.add(rootProject.file("tests/assets").absolutePath)
        }
    }
}

val adbExecutable = androidComponents.sdkComponents.adb

dependencies {
    implementation(project(":samples:basic:core"))
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_android:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:wgpu_android_jni:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:vulkan_android_jni:${LibExt.fdxSnapshotVersion}")
    } else {
        implementation(project(":libfdx:backends:android"))
        implementation(project(":libfdx:extensions:graphics:wgpu:platform:android_jni"))
        implementation(project(":libfdx:extensions:graphics:vulkan:platform:android_jni"))
    }
}

base {
    archivesName.set("sample_basic_android")
}

tasks.register<Exec>("basic_android_gles_run") {
    group = "application"
    description = "Installs and launches the Android GLES basic sample."
    dependsOn("installDebug")
    doFirst {
        commandLine(adbExecutable.get().asFile.absolutePath, "shell", "am", "start", "-n",
                "io.github.libfdx.samples.basic.android/io.github.libfdx.samples.basic.android.BasicAndroidGlesActivity")
    }
}

tasks.register<Exec>("basic_android_wgpu_jni_run") {
    group = "application"
    description = "Installs and launches the Android WGPU JNI basic sample."
    dependsOn("installDebug")
    doFirst {
        commandLine(adbExecutable.get().asFile.absolutePath, "shell", "am", "start", "-n",
                "io.github.libfdx.samples.basic.android/io.github.libfdx.samples.basic.android.BasicAndroidWgpuActivity")
    }
}

tasks.register<Exec>("basic_android_vulkan_run") {
    group = "application"
    description = "Installs and launches the Android Vulkan basic sample."
    dependsOn("installDebug")
    doFirst {
        commandLine(adbExecutable.get().asFile.absolutePath, "shell", "am", "start", "-n",
                "io.github.libfdx.samples.basic.android/io.github.libfdx.samples.basic.android.BasicAndroidVulkanActivity")
    }
}

tasks.register<Exec>("basic_android_vulkan_fallback_run") {
    group = "application"
    description = "Installs and launches the Android Vulkan fallback basic sample."
    dependsOn("installDebug")
    doFirst {
        commandLine(adbExecutable.get().asFile.absolutePath, "shell", "am", "start", "-n",
                "io.github.libfdx.samples.basic.android/io.github.libfdx.samples.basic.android.BasicAndroidVulkanFallbackActivity")
    }
}
