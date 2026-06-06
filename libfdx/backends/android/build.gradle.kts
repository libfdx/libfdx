plugins {
    alias(libs.plugins.android.library)
}

val androidCompileSdkVersion = providers.gradleProperty("androidCompileSdk").get().toInt()
val androidMinSdkVersion = providers.gradleProperty("androidMinSdk").get().toInt()
val freetypeVersion = "2.14.3"
val freetypeSourceDir = rootProject.file("libfdx/runtime/core/build/third-party/freetype/freetype-$freetypeVersion")

fun cmakePath(file: File): String = file.absolutePath.replace('\\', '/')

android {
    namespace = "io.github.libfdx.backend.android"
    compileSdk = androidCompileSdkVersion

    defaultConfig {
        minSdk = androidMinSdkVersion

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_static",
                    "-DLIBFDX_FREETYPE_SOURCE_DIR=${cmakePath(freetypeSourceDir)}",
                    "-DLIBFDX_RUNTIME_CORE_NATIVE_DIR=${cmakePath(rootProject.file("libfdx/runtime/core/src/main/resources/libfdx-native/desktop/runtime_core"))}"
                )
                cppFlags += listOf("-std=c++17", "-fexceptions")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            buildStagingDirectory = layout.buildDirectory.dir("cxx").get().asFile
        }
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
    archivesName.set("backend_android")
}

dependencies {
    api(project(":libfdx:runtime:core"))
    implementation(project(":libfdx:foundation:math"))
    api(project(":libfdx:assets:manager"))
    api(project(":libfdx:runtime:application"))
    api(project(":libfdx:runtime:display"))
    api(project(":libfdx:runtime:files"))
    api(project(":libfdx:runtime:input"))
    api(project(":libfdx:graphics:api"))
    api(project(":libfdx:extensions:graphics:gl:core"))
    api(project(":libfdx:extensions:graphics:vulkan:core"))
}

tasks.configureEach {
    if (name.startsWith("configureCMake") || name.startsWith("externalNativeBuild")) {
        dependsOn(":libfdx:runtime:core:extract_freetype_source")
    }
}
