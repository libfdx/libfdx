plugins {
    id("io.github.libfdx")
}

libfdx {
    assets(rootProject.layout.projectDirectory.dir("assets"))

    desktopC {
        target("opengl") {
            displayName.set("ECS platformer desktop_c GL sample")
            mainClass.set("io.github.libfdx.samples.ecs.platformer.desktopc.EcsPlatformerDesktopCLauncher")
            targetFileName.set("libfdx-ecs-platformer-gl-desktop-c")
        }
    }
}
