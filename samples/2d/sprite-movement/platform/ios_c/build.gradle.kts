
plugins {
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}


base {
    archivesName.set("sample_2d_sprite_movement_ios_c")
}

val sampleProjectPath = project.path.substringBefore(":platform:")
val sampleRoot = layout.projectDirectory.dir("../..")

dependencies {
    implementation(project("$sampleProjectPath:core"))
    implementation(project(":libfdx:backends:ios_c"))
}

libfdx {
    assets(sampleRoot.dir("assets"))

    iosC {
        bundleIdentifier.set("io.github.libfdx.samples.g2d.spritemovement.iosc")

        target("gles") {
            displayName.set("2D Sprite Movement iOS C GLES sample")
            mainClass.set("io.github.libfdx.samples.g2d.spritemovement.iosc.SpriteMovementIosCLauncher")
            targetFileName.set("libfdx-sprite-movement-gles-ios-c")
            graphicsApi.set("gles")
        }
        target("metal") {
            displayName.set("2D Sprite Movement iOS C Metal sample")
            mainClass.set("io.github.libfdx.samples.g2d.spritemovement.iosc.SpriteMovementIosCMetalLauncher")
            targetFileName.set("libfdx-sprite-movement-metal-ios-c")
            graphicsApi.set("metal")
        }
    }
}
