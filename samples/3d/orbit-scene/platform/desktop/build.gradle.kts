plugins {
    id("io.github.libfdx")
}

val sampleProjectPath = project.path.substringBefore(":platform:")

dependencies {
    implementation(project("$sampleProjectPath:core"))
    implementation(project(":libfdx:backends:desktop"))
    runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop"))
}

libfdx {
    desktopJvm {
        mainClass.set("io.github.libfdx.samples.orbitscene.desktop.OrbitSceneDesktopLauncher")
        forwardSystemProperty("libfdx.sample.exitAfterFrames")
        target("gl") {
            displayName.set("OpenGL")
        }
    }
}
