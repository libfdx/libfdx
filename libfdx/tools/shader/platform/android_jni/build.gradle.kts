plugins {
    alias(libs.plugins.android.library)
}

val androidCompileSdkVersion = providers.gradleProperty("androidCompileSdk").get().toInt()
val androidMinSdkVersion = providers.gradleProperty("androidMinSdk").get().toInt()
val androidTargetSdkVersion = providers.gradleProperty("androidTargetSdk").get().toInt()

android {
    namespace = "io.github.libfdx.tools.shader.android"
    compileSdk = androidCompileSdkVersion

    defaultConfig {
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(project(":libfdx:tools:shader:core").layout.buildDirectory.dir("native/shaderc/android"))
        }
    }
}

base {
    archivesName.set("shader_compiler_android_jni")
}

dependencies {
    api(project(":libfdx:tools:shader:core"))
}

tasks.named("preBuild") {
    dependsOn(":libfdx:tools:shader:core:build_shaderc_android")
}
