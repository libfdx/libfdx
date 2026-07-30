
plugins {
    alias(libs.plugins.android.application)
    id("io.github.libfdx")
}

val sampleProjectPath = project.path.substringBefore(":platform:")
val sampleRoot = layout.projectDirectory.dir("../..")
val libfdxDependencyVersion =
    gradle.extensions.extraProperties.get("libfdxDependencyVersion") as String
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
            assets.directories.add(sampleRoot.dir("assets").asFile.absolutePath)
        }
    }
}

dependencies {
    implementation(project("$sampleProjectPath:core"))
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_android:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:wgpu_android_jni:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:vulkan_android_jni:$libfdxDependencyVersion")
    } else {
        implementation(project(":libfdx:backends:android"))
        implementation(project(":libfdx:extensions:graphics:wgpu:platform:android_jni"))
        implementation(project(":libfdx:extensions:graphics:vulkan:platform:android_jni"))
    }
}

base {
    archivesName.set("sample_2d_sprite_movement_android")
}

libfdx {
    android {
        applicationId.set("io.github.libfdx.samples.g2d.spritemovement.android")
        adbExecutable.set(androidComponents.sdkComponents.adb)

        target("gles") {
            displayName.set("2D Sprite Movement GLES sample")
            activity.set(
                "io.github.libfdx.samples.g2d.spritemovement.android.SpriteMovementAndroidGlesActivity"
            )
            runDescription.set("Installs and launches the Android GLES 2D Sprite Movement sample.")
        }
        target("wgpu_jni") {
            displayName.set("2D Sprite Movement WGPU JNI sample")
            activity.set(
                "io.github.libfdx.samples.g2d.spritemovement.android.SpriteMovementAndroidWgpuActivity"
            )
            runDescription.set("Installs and launches the Android WGPU JNI 2D Sprite Movement sample.")
        }
        target("vulkan") {
            displayName.set("2D Sprite Movement Vulkan sample")
            activity.set(
                "io.github.libfdx.samples.g2d.spritemovement.android.SpriteMovementAndroidVulkanActivity"
            )
            runDescription.set("Installs and launches the Android Vulkan 2D Sprite Movement sample.")
        }
        target("vulkan_fallback") {
            displayName.set("2D Sprite Movement Vulkan fallback sample")
            activity.set(
                "io.github.libfdx.samples.g2d.spritemovement.android.SpriteMovementAndroidVulkanFallbackActivity"
            )
            runDescription.set("Installs and launches the Android Vulkan fallback 2D Sprite Movement sample.")
        }
    }
}
