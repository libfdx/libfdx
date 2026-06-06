plugins {
    alias(libs.plugins.android.library)
}

val androidCompileSdkVersion = providers.gradleProperty("androidCompileSdk").get().toInt()
val androidMinSdkVersion = providers.gradleProperty("androidMinSdk").get().toInt()
val freetypeVersion = "2.14.3"
val freetypeSourceDir = rootProject.file("libfdx/runtime/fdx/platform/shared/build/third-party/freetype/freetype-$freetypeVersion")

fun cmakePath(file: File): String = file.absolutePath.replace('\\', '/')

android {
    namespace = "io.github.libfdx.runtime.fdx.android"
    compileSdk = androidCompileSdkVersion

    defaultConfig {
        minSdk = androidMinSdkVersion

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_static",
                    "-DLIBFDX_FREETYPE_SOURCE_DIR=${cmakePath(freetypeSourceDir)}",
                    "-DLIBFDX_RUNTIME_FDX_NATIVE_DIR=${cmakePath(rootProject.file("libfdx/runtime/fdx/platform/shared/src/main/cpp/runtime_fdx"))}"
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
    archivesName.set("fdx_android")
}

tasks.configureEach {
    if (name.startsWith("configureCMake") || name.startsWith("externalNativeBuild")) {
        dependsOn(":libfdx:runtime:fdx:platform:shared:extract_freetype_source")
    }
}
