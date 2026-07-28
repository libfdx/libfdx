plugins {
    alias(libs.plugins.android.application)
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

val adbExecutable = androidComponents.sdkComponents.adb

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

fun registerAndroidRun(
    taskName: String,
    descriptionText: String,
    activity: String
) = tasks.register<Exec>(taskName) {
    group = "application"
    description = descriptionText
    dependsOn("installDebug")
    doFirst {
        commandLine(
            adbExecutable.get().asFile.absolutePath,
            "shell",
            "am",
            "start",
            "-n",
            "io.github.libfdx.samples.starter.android/$activity"
        )
    }
}

registerAndroidRun(
    "starter_project_android_gles_run",
    "Installs and launches the Android OpenGL ES Starter Project sample.",
    "io.github.libfdx.samples.starter.android.StarterProjectAndroidGlesActivity"
)
registerAndroidRun(
    "starter_project_android_wgpu_jni_run",
    "Installs and launches the Android WGPU JNI Starter Project sample.",
    "io.github.libfdx.samples.starter.android.StarterProjectAndroidWgpuActivity"
)
registerAndroidRun(
    "starter_project_android_vulkan_run",
    "Installs and launches the Android Vulkan Starter Project sample.",
    "io.github.libfdx.samples.starter.android.StarterProjectAndroidVulkanActivity"
)
registerAndroidRun(
    "starter_project_android_vulkan_fallback_run",
    "Installs and launches the Android Vulkan-to-OpenGL fallback Starter Project sample.",
    "io.github.libfdx.samples.starter.android.StarterProjectAndroidVulkanFallbackActivity"
)
