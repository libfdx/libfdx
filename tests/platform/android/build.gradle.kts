import io.github.libfdx.build.LibExt

import java.io.File
import java.util.Properties

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

fun adbExecutable(): String {
    val executable = if (System.getProperty("os.name").lowercase().contains("win")) "adb.exe" else "adb"
    val sdkRoots = mutableListOf<String>()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        val localProperties = Properties()
        localPropertiesFile.inputStream().use { localProperties.load(it) }
        localProperties.getProperty("sdk.dir")?.let { sdkRoots += it }
    }
    System.getenv("ANDROID_HOME")?.let { sdkRoots += it }
    System.getenv("ANDROID_SDK_ROOT")?.let { sdkRoots += it }
    sdkRoots.asSequence()
            .map { file("$it/platform-tools/$executable") }
            .firstOrNull { it.isFile }
            ?.let { return it.absolutePath }

    System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .asSequence()
            .map { File(it, executable) }
            .firstOrNull { it.isFile }
            ?.let { return it.absolutePath }

    throw GradleException("Could not find $executable. Set sdk.dir in local.properties, set ANDROID_HOME or ANDROID_SDK_ROOT, or add adb to PATH.")
}

fun registerAndroidRunTask(name: String, installTask: String, applicationId: String, activityName: String) {
    tasks.register<Exec>(name) {
        group = "application"
        description = "Installs and launches the Android graphics test app."
        dependsOn(installTask)
        val command = mutableListOf(adbExecutable(), "shell", "am", "start", "-n",
                "$applicationId/$activityName")
        System.getProperties().stringPropertyNames()
                .filter { it.startsWith("libfdx.test.") || it.startsWith("libfdx.validation.") }
                .sorted()
                .forEach { key ->
                    val value = System.getProperty(key)
                    if (!value.isNullOrBlank()) {
                        command.addAll(listOf("--es", key, value))
                    }
                }
        commandLine(command)
    }
}

fun registerAndroidBuildTask(name: String, descriptionText: String) {
    tasks.register(name) {
        group = "application"
        description = descriptionText
        dependsOn("assembleDebug")
    }
}

registerAndroidBuildTask("test_android_gles_build", "Builds the Android graphics test app for GLES.")
registerAndroidBuildTask("test_android_wgpu_jni_build", "Builds the Android graphics test app for WGPU JNI.")
registerAndroidBuildTask("test_android_vulkan_build", "Builds the Android graphics test app for Vulkan.")

registerAndroidRunTask("test_android_gles_run", "installDebug", "io.github.libfdx.tests.android",
        "io.github.libfdx.tests.android.AndroidGlesTestActivity")
registerAndroidRunTask("test_android_wgpu_jni_run", "installDebug", "io.github.libfdx.tests.android",
        "io.github.libfdx.tests.android.AndroidWgpuTestActivity")
registerAndroidRunTask("test_android_vulkan_run", "installDebug", "io.github.libfdx.tests.android",
        "io.github.libfdx.tests.android.AndroidVulkanTestActivity")
