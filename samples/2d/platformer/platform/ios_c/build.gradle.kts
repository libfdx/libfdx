plugins {
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("sample_platformer_ios_c")
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
        bundleIdentifier.set("io.github.libfdx.samples.g2d.platformer.iosc")

        target("gles") {
            displayName.set("Platformer iOS C GLES sample")
            mainClass.set("io.github.libfdx.samples.g2d.platformer.iosc.PlatformerIosCLauncher")
            targetFileName.set("libfdx-platformer-gles-ios-c")
            graphicsApi.set("gles")
        }
        target("metal") {
            displayName.set("Platformer iOS C Metal sample")
            mainClass.set("io.github.libfdx.samples.g2d.platformer.iosc.PlatformerIosCMetalLauncher")
            targetFileName.set("libfdx-platformer-metal-ios-c")
            graphicsApi.set("metal")
        }
    }
}
