import io.github.libfdx.build.LibExt

plugins {
    alias(libs.plugins.android.application)
}

val androidCompileSdkVersion = providers.gradleProperty("androidCompileSdk").get().toInt()
val androidMinSdkVersion = providers.gradleProperty("androidMinSdk").get().toInt()
val androidTargetSdkVersion = providers.gradleProperty("androidTargetSdk").get().toInt()

group = "${LibExt.fdxGroup}.tests"

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
        sourceCompatibility = JavaVersion.toVersion(25)
        targetCompatibility = JavaVersion.toVersion(25)
    }

    sourceSets {
        getByName("main") {
            assets.srcDir(rootProject.file("tests/assets"))
        }
    }
}

dependencies {
    implementation(project(":tests:core"))
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_android:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:wgpu_android_jni:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:vulkan_android_jni:${LibExt.publishedLibfdxVersion}")
    } else {
        implementation(project(":libfdx:backends:android"))
        implementation(project(":libfdx:extensions:graphics:wgpu:platform:android_jni"))
        implementation(project(":libfdx:extensions:graphics:vulkan:platform:android_jni"))
    }
}

base {
    archivesName.set("tests_android")
}

tasks.register<Exec>("test_android_gles_run") {
    group = "application"
    description = "Installs and launches the Android GLES graphics test app."
    dependsOn("installDebug")
    doFirst {
        commandLine(mutableListOf(android.adbExecutable.absolutePath, "shell", "am", "start", "-n",
                "io.github.libfdx.tests.android/io.github.libfdx.tests.android.AndroidGlesTestActivity").apply {
            System.getProperties().stringPropertyNames()
                    .filter { it.startsWith("libfdx.test.") || it.startsWith("libfdx.validation.") }
                    .sorted()
                    .forEach { key ->
                        val value = System.getProperty(key)
                        if (!value.isNullOrBlank()) {
                            addAll(listOf("--es", key, value))
                        }
                    }
        })
    }
}

tasks.register<Exec>("test_android_wgpu_jni_run") {
    group = "application"
    description = "Installs and launches the Android WGPU JNI graphics test app."
    dependsOn("installDebug")
    doFirst {
        commandLine(mutableListOf(android.adbExecutable.absolutePath, "shell", "am", "start", "-n",
                "io.github.libfdx.tests.android/io.github.libfdx.tests.android.AndroidWgpuTestActivity").apply {
            System.getProperties().stringPropertyNames()
                    .filter { it.startsWith("libfdx.test.") || it.startsWith("libfdx.validation.") }
                    .sorted()
                    .forEach { key ->
                        val value = System.getProperty(key)
                        if (!value.isNullOrBlank()) {
                            addAll(listOf("--es", key, value))
                        }
                    }
        })
    }
}

tasks.register<Exec>("test_android_vulkan_run") {
    group = "application"
    description = "Installs and launches the Android Vulkan graphics test app."
    dependsOn("installDebug")
    doFirst {
        commandLine(mutableListOf(android.adbExecutable.absolutePath, "shell", "am", "start", "-n",
                "io.github.libfdx.tests.android/io.github.libfdx.tests.android.AndroidVulkanTestActivity").apply {
            System.getProperties().stringPropertyNames()
                    .filter { it.startsWith("libfdx.test.") || it.startsWith("libfdx.validation.") }
                    .sorted()
                    .forEach { key ->
                        val value = System.getProperty(key)
                        if (!value.isNullOrBlank()) {
                            addAll(listOf("--es", key, value))
                        }
                    }
        })
    }
}
