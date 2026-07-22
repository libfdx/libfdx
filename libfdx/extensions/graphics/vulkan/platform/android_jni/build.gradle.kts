
plugins {
    id("maven-publish")
    alias(libs.plugins.android.library)
}

val androidCompileSdkVersion = providers.gradleProperty("androidCompileSdk").get().toInt()
val androidMinSdkVersion = providers.gradleProperty("androidMinSdk").get().toInt()
val androidNdkVersion = providers.gradleProperty("androidNdkVersion").get()


android {
    namespace = "io.github.libfdx.graphics.vulkan.android"
    compileSdk = androidCompileSdkVersion
    ndkVersion = androidNdkVersion

    defaultConfig {
        minSdk = androidMinSdkVersion

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-fexceptions")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            buildStagingDirectory = layout.projectDirectory.dir(".cxx").asFile
        }
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

val moduleName = "vulkan_android_jni"

base {
    archivesName.set(moduleName)
}

tasks.named<Delete>("clean") {
    delete(layout.projectDirectory.dir(".cxx"))
}

dependencies {
    api(project(":libfdx:extensions:graphics:vulkan:core"))
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
