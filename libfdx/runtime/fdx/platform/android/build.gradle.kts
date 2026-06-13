plugins {
    alias(libs.plugins.android.library)
}

val androidCompileSdkVersion = providers.gradleProperty("androidCompileSdk").get().toInt()
val androidMinSdkVersion = providers.gradleProperty("androidMinSdk").get().toInt()

android {
    namespace = "io.github.libfdx.runtime.fdx.android"
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
    archivesName.set("fdx_android")
}

tasks.register("prepare_runtime_fdx_android_native") {
    group = "libfdx native"
    description = "Prepares native dependencies used by runtime fdx Android builds."
    dependsOn(":libfdx:runtime:fdx:platform:shared:prepare_runtime_fdx_shared")
}
