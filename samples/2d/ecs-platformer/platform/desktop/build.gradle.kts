plugins {
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("sample_ecs_platformer_desktop")
}

val sampleProjectPath = project.path.substringBefore(":platform:")
val sampleRoot = layout.projectDirectory.dir("../..")
val libfdxDependencyVersion =
    gradle.extensions.extraProperties.get("libfdxDependencyVersion") as String

dependencies {
    implementation(project("$sampleProjectPath:core"))
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:application:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:display:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:d3d12_core:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:wgpu_core:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:backend_desktop:$libfdxDependencyVersion")
        runtimeOnly("${libs.versions.libfdxGroup.get()}:gl_desktop:$libfdxDependencyVersion")
        runtimeOnly("${libs.versions.libfdxGroup.get()}:vulkan_desktop:$libfdxDependencyVersion")
        runtimeOnly("${libs.versions.libfdxGroup.get()}:wgpu_desktop_ffm:$libfdxDependencyVersion")
    } else {
        implementation(project(":libfdx:framework:application"))
        implementation(project(":libfdx:framework:display"))
        implementation(project(":libfdx:extensions:graphics:d3d12:core"))
        implementation(project(":libfdx:extensions:graphics:wgpu:core"))
        implementation(project(":libfdx:backends:desktop"))
        runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop"))
        runtimeOnly(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
        runtimeOnly(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
    }
}

libfdx {
    assets(sampleRoot.dir("assets"))

    desktopJvm {
        mainClass.set("io.github.libfdx.samples.ecs.platformer.desktop.EcsPlatformerDesktopLauncher")
        workingDir.set(sampleRoot)
        forwardSystemProperty("libfdx.sample.exitAfterFrames")
        forwardSystemProperty("libfdx.sample.capture")
        forwardSystemProperty("libfdx.sample.captureFrame")
        forwardSystemProperty("libfdx.sample.maximized")
        target("gl") {
            displayName.set("GL")
            systemProperty("libfdx.sample.graphics", "gl")
            systemProperty("libfdx.sample.graphicsLabel", "GL")
            launchProperty("graphics", "gl")
            launchProperty("graphicsLabel", "GL")
            buildDescription.set("Builds the ECS platformer desktop GL release jar.")
            runDescription.set("Runs the ECS platformer desktop sample with GL.")
        }
        target("wgpu") {
            displayName.set("WGPU")
            systemProperty("libfdx.sample.graphics", "wgpu")
            systemProperty("libfdx.sample.graphicsLabel", "WGPU")
            launchProperty("graphics", "wgpu")
            launchProperty("graphicsLabel", "WGPU")
            buildDescription.set("Builds the ECS platformer desktop WGPU release jar.")
            runDescription.set("Runs the ECS platformer desktop sample with WGPU.")
        }
        target("vulkan") {
            displayName.set("Vulkan")
            systemProperty("libfdx.sample.graphics", "vulkan")
            systemProperty("libfdx.sample.graphicsLabel", "Vulkan")
            launchProperty("graphics", "vulkan")
            launchProperty("graphicsLabel", "Vulkan")
            buildDescription.set("Builds the ECS platformer desktop Vulkan release jar.")
            runDescription.set("Runs the ECS platformer desktop sample with Vulkan.")
        }
        target("d3d12") {
            displayName.set("Direct3D 12")
            systemProperty("libfdx.sample.graphics", "d3d12")
            systemProperty("libfdx.sample.graphicsLabel", "Direct3D 12")
            launchProperty("graphics", "d3d12")
            launchProperty("graphicsLabel", "Direct3D 12")
            buildDescription.set("Builds the ECS platformer desktop Direct3D 12 release jar.")
            runDescription.set("Runs the ECS platformer desktop sample with Direct3D 12 on Windows.")
        }
    }
}
