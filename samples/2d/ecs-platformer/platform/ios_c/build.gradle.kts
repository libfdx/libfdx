plugins {
    id("io.github.libfdx")
}

libfdx {
    assets(rootProject.layout.projectDirectory.dir("assets"))

    iosC {
        bundleIdentifier.set("io.github.libfdx.samples.ecs.platformer.iosc")

        target("gles") {
            displayName.set("ECS platformer iOS C GLES sample")
            mainClass.set("io.github.libfdx.samples.ecs.platformer.iosc.EcsPlatformerIosCLauncher")
            targetFileName.set("libfdx-ecs-platformer-gles-ios-c")
            graphicsApi.set("gles")
        }
        target("metal") {
            displayName.set("ECS platformer iOS C Metal sample")
            mainClass.set("io.github.libfdx.samples.ecs.platformer.iosc.EcsPlatformerIosCMetalLauncher")
            targetFileName.set("libfdx-ecs-platformer-metal-ios-c")
            graphicsApi.set("metal")
        }
    }
}
