import io.github.libfdx.build.LibExt
import org.gradle.api.GradleException
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class PrepareRuntimeFdxAndroidNativeTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        if (!outputDirectory.get().asFile.isDirectory) {
            throw GradleException("Runtime fdx Android JNI output was not generated")
        }
    }
}

plugins {
    id("maven-publish")
    alias(libs.plugins.android.library)
}

group = "${LibExt.fdxGroup}.runtime.fdx"

val androidCompileSdkVersion = providers.gradleProperty("androidCompileSdk").get().toInt()
val androidMinSdkVersion = providers.gradleProperty("androidMinSdk").get().toInt()
val fdxBuildProject = project(":libfdx:framework:fdx:fdx-build")
val runtimeFdxAndroidJniLibs = fdxBuildProject.layout.buildDirectory.dir("generated/jniLibs/runtimeFdxAndroid")
val runtimeFdxAndroidAbis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

android {
    namespace = "io.github.libfdx.runtime.fdx.android"
    compileSdk = androidCompileSdkVersion

    defaultConfig {
        minSdk = androidMinSdkVersion
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

val moduleName = "fdx_android"

base {
    archivesName.set(moduleName)
}

val prepareRuntimeFdxAndroidNative = tasks.register<PrepareRuntimeFdxAndroidNativeTask>("prepare_runtime_fdx_android_native") {
    group = "libfdx native"
    description = "Builds runtime fdx Android JNI libraries through fdx-build before packaging."
    dependsOn(":libfdx:framework:fdx:fdx-build:build_runtime_fdx_android_native")
    outputDirectory.set(runtimeFdxAndroidJniLibs)
}

androidComponents.onVariants { variant ->
    variant.sources.jniLibs?.addGeneratedSourceDirectory(
        prepareRuntimeFdxAndroidNative,
        PrepareRuntimeFdxAndroidNativeTask::outputDirectory
    )
}

tasks.register("assemble_runtime_fdx_android_release_prebuilt") {
    group = "libfdx native"
    description = "Builds the runtime fdx Android release AAR using fdx-natives prebuilt dependencies."
    dependsOn("assembleRelease")
}

tasks.named("preBuild") {
    dependsOn(prepareRuntimeFdxAndroidNative)
}

tasks.register("validate_runtime_fdx_android_jni_libs") {
    group = "libfdx native"
    description = "Validates generated fdx_android JNI libraries before packaging."
    doLast {
        val root = runtimeFdxAndroidJniLibs.get().asFile
        val missing = runtimeFdxAndroidAbis
            .map { root.resolve("$it/libfdx.so") }
            .filterNot(File::isFile)
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing generated fdx_android JNI libraries:\n" +
                    missing.joinToString(separator = "\n") { " - ${it.absolutePath}" } + "\n" +
                    "Run :libfdx:framework:fdx:platform:android:prepare_runtime_fdx_android_native first."
            )
        }
    }
}
val androidJavadocJar = tasks.register("androidJavadocJar", org.gradle.api.tasks.bundling.Jar::class) {
    archiveClassifier.set("javadoc")
}

tasks.register("androidSourcesJar", org.gradle.api.tasks.bundling.Jar::class) {
    archiveClassifier.set("sources")
    from(layout.projectDirectory.dir("src/main/java"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = moduleName
            artifact(androidJavadocJar)
        }
    }
}

afterEvaluate {
    publishing {
        publications.named<MavenPublication>("maven") {
            from(components["release"])
        }
    }
}
