import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    id("java")
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

val glRuntimeClasspath = configurations.create("glRuntimeClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val vulkanRuntimeClasspath = configurations.create("vulkanRuntimeClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val wgpuRuntimeClasspath = configurations.create("wgpuRuntimeClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

base {
    archivesName.set("sample_base_starter_project_plugin")
}

val sampleProjectPath = project.path.substringBefore(":platform:")
val sampleRoot = layout.projectDirectory.dir("../..")
val desktopProject = findProject("$sampleProjectPath:platform:desktop")
val desktopCProject = findProject("$sampleProjectPath:platform:desktop_c")
val iosCProject = findProject("$sampleProjectPath:platform:ios_c")
val webProject = findProject("$sampleProjectPath:platform:web")
val libfdxDependencyVersion =
    gradle.extensions.extraProperties.get("libfdxDependencyVersion") as String

dependencies {
    desktopProject?.let { implementation(it) }
    desktopCProject?.let { implementation(it) }
    iosCProject?.let { implementation(it) }
    webProject?.let { implementation(it) }

    if (desktopProject != null) {
        if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
            glRuntimeClasspath("${libs.versions.libfdxGroup.get()}:gl_desktop:$libfdxDependencyVersion")
            vulkanRuntimeClasspath("${libs.versions.libfdxGroup.get()}:vulkan_desktop:$libfdxDependencyVersion")
            wgpuRuntimeClasspath("${libs.versions.libfdxGroup.get()}:wgpu_desktop_ffm:$libfdxDependencyVersion")
        } else {
            glRuntimeClasspath(project(":libfdx:extensions:graphics:gl:platform:desktop"))
            vulkanRuntimeClasspath(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
            wgpuRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
        }
    }
}

libfdx {
    assets(sampleRoot.dir("assets"))

    if (desktopProject != null) {
        desktopJvm {
            mainClass.set("io.github.libfdx.samples.starter.desktop.StarterProjectDesktopLauncher")
            workingDir.set(sampleRoot)
            forwardSystemProperty("libfdx.sample.exitAfterFrames")
            forwardSystemProperty("libfdx.sample.maximized")

            target("gl") {
                displayName.set("GL")
                runtimeClasspath(glRuntimeClasspath)
                systemProperty("libfdx.sample.graphics", "gl")
                systemProperty("libfdx.sample.graphicsLabel", "GL")
                launchProperty("graphics", "gl")
                launchProperty("graphicsLabel", "GL")
                buildDescription.set("Builds the Starter Project desktop GL release jar.")
                runDescription.set("Runs the Starter Project desktop sample with GL.")
            }
            target("wgpu") {
                displayName.set("WGPU")
                runtimeClasspath(wgpuRuntimeClasspath)
                systemProperty("libfdx.sample.graphics", "wgpu")
                systemProperty("libfdx.sample.graphicsLabel", "WGPU")
                launchProperty("graphics", "wgpu")
                launchProperty("graphicsLabel", "WGPU")
                buildDescription.set("Builds the Starter Project desktop WGPU release jar.")
                runDescription.set("Runs the Starter Project desktop sample with WGPU.")
            }
            target("vulkan") {
                displayName.set("Vulkan")
                runtimeClasspath(vulkanRuntimeClasspath)
                systemProperty("libfdx.sample.graphics", "vulkan")
                systemProperty("libfdx.sample.graphicsLabel", "Vulkan")
                launchProperty("graphics", "vulkan")
                launchProperty("graphicsLabel", "Vulkan")
                buildDescription.set("Builds the Starter Project desktop Vulkan release jar.")
                runDescription.set("Runs the Starter Project desktop sample with Vulkan.")
            }
            target("d3d12") {
                displayName.set("Direct3D 12")
                systemProperty("libfdx.sample.graphics", "d3d12")
                systemProperty("libfdx.sample.graphicsLabel", "Direct3D 12")
                launchProperty("graphics", "d3d12")
                launchProperty("graphicsLabel", "Direct3D 12")
                buildDescription.set("Builds the Starter Project desktop Direct3D 12 release jar.")
                runDescription.set("Runs the Starter Project desktop sample with Direct3D 12 on Windows.")
            }
        }
    }

    if (webProject != null) {
        js {
            mainClass.set("io.github.libfdx.samples.starter.web.StarterProjectWebJsLauncher")
            htmlTitle.set("libFDX Starter Project - Web")
            canvasId.set("libfdx-canvas")
            htmlWidth.set(0)
            htmlHeight.set(0)

            target("webgl") {
                buildDescription.set("Builds the Starter Project WebGL JavaScript web application.")
                runDescription.set("Builds and serves the Starter Project WebGL JavaScript web application.")
            }

            target("webgpu") {
                defaultPath.set("/?graphics=webgpu")
                buildDescription.set("Builds the Starter Project WebGPU JavaScript web application.")
                runDescription.set("Builds and serves the Starter Project WebGPU JavaScript web application.")
            }
        }

        wasm {
            mainClass.set("io.github.libfdx.samples.starter.web.StarterProjectWebWasmLauncher")
            htmlTitle.set("libFDX Starter Project - WebAssembly")
            canvasId.set("libfdx-canvas")
            htmlWidth.set(0)
            htmlHeight.set(0)

            target("webgl") {
                buildDescription.set("Builds the Starter Project WebGL WebAssembly web application.")
                runDescription.set("Builds and serves the Starter Project WebGL WebAssembly web application.")
            }
        }
    }

    if (desktopCProject != null) {
        desktopC {
            showConsole.set(providers.gradleProperty("libfdx.desktopC.showConsole")
                    .map { value -> value.toBooleanStrictOrNull() ?: value.toBoolean() }
                    .orElse(true))

            target("opengl") {
                displayName.set("Starter Project desktop C OpenGL sample")
                mainClass.set("io.github.libfdx.samples.starter.desktopc.StarterProjectDesktopCLauncher")
                targetFileName.set("libfdx-starter-project-gl-desktop-c")
            }
        }
    }

    if (iosCProject != null) {
        iosC {
            bundleIdentifier.set("io.github.libfdx.samples.starter.iosc")

            target("gles") {
                displayName.set("Starter Project iOS C OpenGL ES sample")
                mainClass.set("io.github.libfdx.samples.starter.iosc.StarterProjectIosCLauncher")
                targetFileName.set("libfdx-starter-project-gles-ios-c")
                graphicsApi.set("gles")
            }
            target("metal") {
                displayName.set("Starter Project iOS C Metal sample")
                mainClass.set("io.github.libfdx.samples.starter.iosc.StarterProjectIosCMetalLauncher")
                targetFileName.set("libfdx-starter-project-metal-ios-c")
                graphicsApi.set("metal")
            }
        }
    }
}
