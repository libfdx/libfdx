plugins {
    alias(libs.plugins.android.application)
    id("io.github.libfdx")
}

val sampleProjectPath = project.path.substringBefore(":platform:")
val appId = "io.github.libfdx.samples.shadergraph.android"

android {
    namespace = appId
    compileSdk = providers.gradleProperty("androidCompileSdk").get().toInt()
    defaultConfig {
        applicationId = appId
        minSdk = providers.gradleProperty("androidMinSdk").get().toInt()
        targetSdk = providers.gradleProperty("androidTargetSdk").get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.getByName("main").assets.directories.add(layout.projectDirectory.dir("../../assets").asFile.absolutePath)
}

dependencies {
    implementation(project("$sampleProjectPath:core"))
    implementation(project(":libfdx:backends:android"))
}

libfdx {
    android {
        applicationId.set(appId)
        adbExecutable.set(androidComponents.sdkComponents.adb)
        target("gles") {
            activity.set("$appId.ShaderGraphActivity")
            runDescription.set("Installs and runs the saved shader graph gallery on Android OpenGL ES.")
        }
    }
}
