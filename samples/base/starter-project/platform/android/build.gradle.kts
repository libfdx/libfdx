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
    namespace = "io.github.libfdx.samples.starter.android"
    compileSdk = androidCompileSdkVersion

    defaultConfig {
        applicationId = "io.github.libfdx.samples.starter.android"
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
    archivesName.set("sample_base_starter_project_android")
}

libfdx {
    android {
        applicationId.set("io.github.libfdx.samples.starter.android")
        adbExecutable.set(androidComponents.sdkComponents.adb)

        target("gles") {
            displayName.set("Starter Project OpenGL ES sample")
            activity.set("io.github.libfdx.samples.starter.android.StarterProjectAndroidGlesActivity")
            runDescription.set("Installs and launches the Android OpenGL ES Starter Project sample.")
        }
        target("wgpu_jni") {
            displayName.set("Starter Project WGPU JNI sample")
            activity.set("io.github.libfdx.samples.starter.android.StarterProjectAndroidWgpuActivity")
            runDescription.set("Installs and launches the Android WGPU JNI Starter Project sample.")
        }
        target("vulkan") {
            displayName.set("Starter Project Vulkan sample")
            activity.set("io.github.libfdx.samples.starter.android.StarterProjectAndroidVulkanActivity")
            runDescription.set("Installs and launches the Android Vulkan Starter Project sample.")
        }
        target("vulkan_fallback") {
            displayName.set("Starter Project Vulkan fallback sample")
            activity.set("io.github.libfdx.samples.starter.android.StarterProjectAndroidVulkanFallbackActivity")
            runDescription.set(
                "Installs and launches the Android Vulkan-to-OpenGL fallback Starter Project sample."
            )
        }
    }
}
