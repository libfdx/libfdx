plugins {
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("sample_platformer_desktop_c")
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
            displayName.set("Platformer desktop_c GL sample")
            mainClass.set("io.github.libfdx.samples.g2d.platformer.desktopc.PlatformerDesktopCLauncher")
            targetFileName.set("libfdx-platformer-gl-desktop-c")
        }
    }
}
