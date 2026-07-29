
plugins {
    alias(libs.plugins.android.application)
    id("io.github.libfdx")
}

val androidCompileSdkVersion = providers.gradleProperty("androidCompileSdk").get().toInt()
val androidMinSdkVersion = providers.gradleProperty("androidMinSdk").get().toInt()
val androidTargetSdkVersion = providers.gradleProperty("androidTargetSdk").get().toInt()


android {
    namespace = "io.github.libfdx.tests.android"
    compileSdk = androidCompileSdkVersion

    defaultConfig {
        applicationId = "io.github.libfdx.tests.android"
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

dependencies {
    implementation(project(":tests:core"))
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
    archivesName.set("tests_android")
}

libfdx {
    android {
        applicationId.set("io.github.libfdx.tests.android")
        adbExecutable.set(androidComponents.sdkComponents.adb)
        forwardStringSystemPropertyPrefix("libfdx.test.")
        forwardStringSystemPropertyPrefix("libfdx.validation.")

        target("gles") {
            displayName.set("Android GLES graphics tests")
            activity.set("io.github.libfdx.tests.android.AndroidGlesTestActivity")
            runDescription.set("Installs and launches the Android GLES graphics test app.")
        }
        target("wgpu_jni") {
            displayName.set("Android WGPU JNI graphics tests")
            activity.set("io.github.libfdx.tests.android.AndroidWgpuTestActivity")
            runDescription.set("Installs and launches the Android WGPU JNI graphics test app.")
        }
        target("vulkan") {
            displayName.set("Android Vulkan graphics tests")
            activity.set("io.github.libfdx.tests.android.AndroidVulkanTestActivity")
            runDescription.set("Installs and launches the Android Vulkan graphics test app.")
        }
    }
}
