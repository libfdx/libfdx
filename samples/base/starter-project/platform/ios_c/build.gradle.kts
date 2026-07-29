plugins {
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("sample_base_starter_project_ios_c")
}

val sampleProjectPath = project.path.substringBefore(":platform:")
val sampleRoot = layout.projectDirectory.dir("../..")
val libfdxDependencyVersion =
    gradle.extensions.extraProperties.get("libfdxDependencyVersion") as String

dependencies {
    implementation(project("$sampleProjectPath:core"))
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_ios_c:$libfdxDependencyVersion")
    } else {
        implementation(project(":libfdx:backends:ios_c"))
    }
}

libfdx {
    assets(sampleRoot.dir("assets"))

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
