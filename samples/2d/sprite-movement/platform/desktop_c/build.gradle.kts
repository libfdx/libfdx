
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
val libfdxDependencyVersion =
    gradle.extensions.extraProperties.get("libfdxDependencyVersion") as String

dependencies {
    implementation(project("$sampleProjectPath:core"))
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_desktop_c:$libfdxDependencyVersion")
        runtimeOnly("${libs.versions.libfdxGroup.get()}:gl_desktop_c:$libfdxDependencyVersion")
    } else {
        implementation(project(":libfdx:backends:desktop_c"))
        runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop_c"))
    }
}

libfdx {
    assets(
        sampleRoot.dir("assets"),
        sampleRoot.dir("scenes")
    )

    desktopC {
        target("opengl") {
            displayName.set("2D Sprite Movement desktop_c GL sample")
            mainClass.set("io.github.libfdx.samples.g2d.spritemovement.desktopc.SpriteMovementDesktopCLauncher")
            targetFileName.set("libfdx-sprite-movement-gl-desktop-c")
        }
    }
}
