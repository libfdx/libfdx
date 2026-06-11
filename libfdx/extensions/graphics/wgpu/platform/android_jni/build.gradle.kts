import io.github.libfdx.build.LibExt

plugins {
    alias(libs.plugins.android.library)
}

val androidCompileSdkVersion = providers.gradleProperty("androidCompileSdk").get().toInt()
val androidMinSdkVersion = providers.gradleProperty("androidMinSdk").get().toInt()

group = "${LibExt.fdxGroup}.wgpu"

android {
    namespace = "io.github.libfdx.graphics.wgpu.android"
    compileSdk = androidCompileSdkVersion

    defaultConfig {
        minSdk = androidMinSdkVersion
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(25)
        targetCompatibility = JavaVersion.toVersion(25)
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

base {
    archivesName.set("wgpu_android_jni")
}

dependencies {
    api(project(":libfdx:extensions:graphics:wgpu:core"))
    api(libs.jwebgpu.android) {
        exclude(group = "com.github.xpenatan.jParser", module = "runtime-jni")
        exclude(group = "com.github.xpenatan.jParser", module = "runtime-jni_linux_x64")
        exclude(group = "com.github.xpenatan.jParser", module = "runtime-jni_mac_arm64")
        exclude(group = "com.github.xpenatan.jParser", module = "runtime-jni_mac_x64")
        exclude(group = "com.github.xpenatan.jParser", module = "runtime-jni_windows_x64")
    }
}
