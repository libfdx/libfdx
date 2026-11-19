plugins {
    id("io.github.libfdx")
}

val sampleProjectPath = project.path.substringBefore(":platform:")

dependencies {
    implementation(project("$sampleProjectPath:core"))
    implementation(project(":libfdx:backends:web"))
    implementation(project(":libfdx:extensions:graphics:gl:platform:web"))
}

libfdx {
    js {
        mainClass.set("io.github.libfdx.samples.scoreboard.web.ScoreboardWebLauncher")
        htmlTitle.set("Scoreboard")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)
        target("webgl") {
            buildDescription.set("Builds the Scoreboard WebGL application.")
        }
    }
}
