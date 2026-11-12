
plugins {
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}


base {
    archivesName.set("sample_2d_sprite_movement_desktop_c")
}

val sampleProjectPath = project.path.substringBefore(":platform:")
val sampleRoot = layout.projectDirectory.dir("../..")

dependencies {
    implementation(project("$sampleProjectPath:core"))
    implementation(project(":libfdx:backends:desktop_c"))
    runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop_c"))
}

libfdx {
    assets(sampleRoot.dir("assets"))

    desktopC {
        target("opengl") {
            displayName.set("2D Sprite Movement desktop_c GL sample")
            mainClass.set("io.github.libfdx.samples.g2d.spritemovement.desktopc.SpriteMovementDesktopCLauncher")
            targetFileName.set("libfdx-sprite-movement-gl-desktop-c")
        }
    }
}
