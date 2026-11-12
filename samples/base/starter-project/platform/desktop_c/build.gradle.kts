plugins {
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("sample_base_starter_project_desktop_c")
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
            displayName.set("Starter Project desktop C OpenGL sample")
            mainClass.set("io.github.libfdx.samples.starter.desktopc.StarterProjectDesktopCLauncher")
            targetFileName.set("libfdx-starter-project-gl-desktop-c")
        }
    }
}
