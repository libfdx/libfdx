import io.github.libfdx.build.LibExt

plugins {
    alias(libs.plugins.android.library)
}

group = "${LibExt.fdxGroup}.runtime.fdx"

val androidCompileSdkVersion = providers.gradleProperty("androidCompileSdk").get().toInt()
val androidMinSdkVersion = providers.gradleProperty("androidMinSdk").get().toInt()
val freetypeVersion = "2.14.3"
val freetypeSourceDir =
    rootProject.layout.projectDirectory.dir("libfdx/runtime/fdx/platform/shared/build/third-party/freetype/freetype-$freetypeVersion")
val runtimeFdxNativeDir = rootProject.layout.projectDirectory.dir("libfdx/runtime/fdx/platform/shared/src/main/cpp/runtime_fdx")
val shaderCompilerSourceDir =
    rootProject.layout.projectDirectory.dir("libfdx/runtime/fdx/platform/shared/src/main/cpp/shader_compiler")
val shaderCompilerDawnSourceDir =
    project(":libfdx:runtime:fdx:platform:shared").layout.buildDirectory.dir("third-party/dawn/source")

val runtimeFdxShaderCompilerEnabled = providers.gradleProperty("libfdx.runtimeFdx.shaderCompiler")
    .map(String::toBoolean)
    .orElse(true)

fun runtimeFdxShaderCompilerCmakeArgs(): List<String> {
    if (!runtimeFdxShaderCompilerEnabled.get()) {
        return listOf("-DLIBFDX_ENABLE_SHADER_COMPILER=OFF")
    }
    return listOf(
        "-DLIBFDX_ENABLE_SHADER_COMPILER=ON",
        "-DLIBFDX_SHADERC_SOURCE_DIR=${shaderCompilerSourceDir.asFile.absolutePath}",
        "-DFDX_DAWN_SOURCE_DIR=${shaderCompilerDawnSourceDir.get().asFile.absolutePath}"
    )
}

fun Task.runtimeFdxNativeInputsDependency() {
    dependsOn(":libfdx:runtime:fdx:platform:shared:extract_freetype_source")
    inputs.dir(runtimeFdxNativeDir)
    if (runtimeFdxShaderCompilerEnabled.get()) {
        dependsOn(":libfdx:runtime:fdx:platform:shared:resolve_runtime_fdx_tint_source")
        inputs.dir(shaderCompilerSourceDir)
    }
}

android {
    namespace = "io.github.libfdx.runtime.fdx.android"
    compileSdk = androidCompileSdkVersion

    defaultConfig {
        minSdk = androidMinSdkVersion
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DLIBFDX_FREETYPE_SOURCE_DIR=${freetypeSourceDir.asFile.absolutePath}",
                    "-DLIBFDX_RUNTIME_FDX_NATIVE_DIR=${runtimeFdxNativeDir.asFile.absolutePath}"
                ) + runtimeFdxShaderCompilerCmakeArgs()
            }
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

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
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
    if (runtimeFdxShaderCompilerEnabled.get()) {
        dependsOn(":libfdx:runtime:fdx:platform:shared:resolve_runtime_fdx_tint_source")
    }
}

tasks.named<Delete>("clean") {
    delete(layout.projectDirectory.dir(".cxx"))
}

tasks.matching {
    it.name.startsWith("configureCMake") ||
        (it.name.startsWith("externalNativeBuild") && !it.name.startsWith("externalNativeBuildClean"))
}.configureEach {
    runtimeFdxNativeInputsDependency()
}
