import io.github.libfdx.build.LibExt

plugins {
    alias(libs.plugins.android.library)
}

val androidCompileSdkVersion = providers.gradleProperty("androidCompileSdk").get().toInt()
val androidMinSdkVersion = providers.gradleProperty("androidMinSdk").get().toInt()

group = "${LibExt.fdxGroup}.webrtc"

android {
    namespace = "io.github.libfdx.net.webrtc.android"
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
    archivesName.set("webrtc_android_jni")
}

dependencies {
    api(project(":libfdx:extensions:net:webrtc:core"))
    api(libs.webrtc.android)
    implementation(libs.java.websocket)
}
