
plugins {
    alias(libs.plugins.android.application)
}

val androidCompileSdkVersion = providers.gradleProperty("androidCompileSdk").get().toInt()
val androidMinSdkVersion = providers.gradleProperty("androidMinSdk").get().toInt()
val androidTargetSdkVersion = providers.gradleProperty("androidTargetSdk").get().toInt()


android {
    namespace = "io.github.libfdx.samples.g2d.spritemovement.android"
    compileSdk = androidCompileSdkVersion

    defaultConfig {
        applicationId = "io.github.libfdx.samples.g2d.spritemovement.android"
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
            assets.directories.add(rootProject.file("samples/2d/sprite-movement/assets").absolutePath)
            assets.directories.add(rootProject.file("samples/2d/sprite-movement/scenes").absolutePath)
        }
    }
}

val adbExecutable = androidComponents.sdkComponents.adb

dependencies {
    implementation(project(":samples:2d:sprite-movement:core"))
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_android:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:wgpu_android_jni:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:vulkan_android_jni:${libs.versions.libfdxSnapshot.get()}")
    } else {
        implementation(project(":libfdx:backends:android"))
        implementation(project(":libfdx:extensions:graphics:wgpu:platform:android_jni"))
        implementation(project(":libfdx:extensions:graphics:vulkan:platform:android_jni"))
    }
}

base {
    archivesName.set("sample_2d_sprite_movement_android")
}

tasks.register<Exec>("sprite_movement_android_gles_run") {
    group = "application"
    description = "Installs and launches the Android GLES 2D Sprite Movement sample."
    dependsOn("installDebug")
    doFirst {
        commandLine(adbExecutable.get().asFile.absolutePath, "shell", "am", "start", "-n",
                "io.github.libfdx.samples.g2d.spritemovement.android/" +
                        "io.github.libfdx.samples.g2d.spritemovement.android.SpriteMovementAndroidGlesActivity")
    }
}

tasks.register<Exec>("sprite_movement_android_wgpu_jni_run") {
    group = "application"
    description = "Installs and launches the Android WGPU JNI 2D Sprite Movement sample."
    dependsOn("installDebug")
    doFirst {
        commandLine(adbExecutable.get().asFile.absolutePath, "shell", "am", "start", "-n",
                "io.github.libfdx.samples.g2d.spritemovement.android/" +
                        "io.github.libfdx.samples.g2d.spritemovement.android.SpriteMovementAndroidWgpuActivity")
    }
}

tasks.register<Exec>("sprite_movement_android_vulkan_run") {
    group = "application"
    description = "Installs and launches the Android Vulkan 2D Sprite Movement sample."
    dependsOn("installDebug")
    doFirst {
        commandLine(adbExecutable.get().asFile.absolutePath, "shell", "am", "start", "-n",
                "io.github.libfdx.samples.g2d.spritemovement.android/" +
                        "io.github.libfdx.samples.g2d.spritemovement.android.SpriteMovementAndroidVulkanActivity")
    }
}

tasks.register<Exec>("sprite_movement_android_vulkan_fallback_run") {
    group = "application"
    description = "Installs and launches the Android Vulkan fallback 2D Sprite Movement sample."
    dependsOn("installDebug")
    doFirst {
        commandLine(adbExecutable.get().asFile.absolutePath, "shell", "am", "start", "-n",
                "io.github.libfdx.samples.g2d.spritemovement.android/" +
                        "io.github.libfdx.samples.g2d.spritemovement.android.SpriteMovementAndroidVulkanFallbackActivity")
    }
}
