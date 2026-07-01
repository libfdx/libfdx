plugins {
    id("io.github.libfdx")
}

libfdx {
    assets(rootProject.layout.projectDirectory.dir("assets"))

    desktopJvm {
        mainClass.set("io.github.libfdx.samples.ecs.platformer.desktop.EcsPlatformerDesktopLauncher")
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
    }
}
