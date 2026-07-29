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
    namespace = "io.github.libfdx.samples.ecs.platformer.android"
    compileSdk = androidCompileSdkVersion

    defaultConfig {
        applicationId = "io.github.libfdx.samples.ecs.platformer.android"
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

base {
    archivesName.set("sample_ecs_platformer_android")
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

libfdx {
    android {
        applicationId.set("io.github.libfdx.samples.ecs.platformer.android")
        adbExecutable.set(androidComponents.sdkComponents.adb)

        target("gles") {
            displayName.set("ECS platformer GLES sample")
            activity.set("io.github.libfdx.samples.ecs.platformer.android.EcsPlatformerAndroidGlesActivity")
            runDescription.set("Installs and launches the Android GLES ECS platformer sample.")
        }
        target("wgpu_jni") {
            displayName.set("ECS platformer WGPU JNI sample")
            activity.set("io.github.libfdx.samples.ecs.platformer.android.EcsPlatformerAndroidWgpuActivity")
            runDescription.set("Installs and launches the Android WGPU JNI ECS platformer sample.")
        }
        target("vulkan") {
            displayName.set("ECS platformer Vulkan sample")
            activity.set("io.github.libfdx.samples.ecs.platformer.android.EcsPlatformerAndroidVulkanActivity")
            runDescription.set("Installs and launches the Android Vulkan ECS platformer sample.")
        }
        target("vulkan_fallback") {
            displayName.set("ECS platformer Vulkan fallback sample")
            activity.set(
                "io.github.libfdx.samples.ecs.platformer.android.EcsPlatformerAndroidVulkanFallbackActivity"
            )
            runDescription.set("Installs and launches the Android Vulkan fallback ECS platformer sample.")
        }
    }
}
