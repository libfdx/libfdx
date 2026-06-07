import io.github.libfdx.build.LibExt

plugins {
    id("base")
}

LibExt.configure(rootProject.projectDir)

allprojects {
    group = LibExt.fdxGroup
    version = LibExt.fdxVersion

    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
        maven {
            url = uri("http://teavm.org/maven/repository/")
            isAllowInsecureProtocol = true
        }
    }

    configurations.configureEach {
        // Check for updates every sync
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }
}

fun runtimeFdxHostNativeTaskPath(): String {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("windows") -> ":libfdx:runtime:fdx:platform:desktop:generate_runtime_fdx_windows_native"
        os.contains("linux") -> ":libfdx:runtime:fdx:platform:desktop:generate_runtime_fdx_linux_native"
        os.contains("mac") || os.contains("darwin") -> ":libfdx:runtime:fdx:platform:desktop:generate_runtime_fdx_macos_native"
        else -> throw GradleException("Unsupported host OS for runtime fdx native artifacts: ${System.getProperty("os.name")}")
    }
}

tasks.register("build_native_artifacts") {
    group = "libfdx native"
    description = "Builds generated native artifacts for the current host, web, and Android release packaging."
    dependsOn(
        runtimeFdxHostNativeTaskPath(),
        ":libfdx:runtime:fdx:platform:web:generate_runtime_fdx_web_native",
        ":libfdx:runtime:fdx:platform:android:assembleRelease",
        ":libfdx:backends:android:assembleRelease",
        ":libfdx:extensions:graphics:vulkan:platform:android_jni:assembleRelease",
        ":libfdx:extensions:graphics:wgpu:platform:android_jni:assembleRelease"
    )
}

tasks.register("printFdxVersion") {
    group = "help"
    description = "Prints the libFDX version configured by LibExt."
    doLast {
        println(LibExt.fdxVersion)
    }
}

if (!LibExt.usePublishedLibfdx) {
    extra["libfdxPublishTarget"] = LibfdxPublishTarget.LIBRARIES
    apply(plugin = "publish")
}
