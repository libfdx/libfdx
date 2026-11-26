plugins {
    id("io.github.libfdx")
}

val sampleProjectPath = project.path.substringBefore(":platform:")

dependencies {
    implementation(project("$sampleProjectPath:core"))
    implementation(project(":libfdx:backends:web"))
    implementation(project(":libfdx:extensions:graphics:gl:platform:web"))
    implementation(project(":libfdx:extensions:graphics:wgpu:platform:web"))
    implementation(libs.teavm.jso)
}

libfdx {
    assets(layout.projectDirectory.dir("../../assets"))
    js {
        mainClass.set("io.github.libfdx.samples.shadergraph.web.ShaderGraphWebLauncher")
        htmlTitle.set("Shader graph gallery")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)
        target("webgl2") {
            runDescription.set("Runs the saved shader graph gallery on WebGL 2.")
        }
        target("webgpu") {
            defaultPath.set("/?graphics=webgpu")
            runDescription.set("Runs the saved shader graph gallery on WebGPU.")
        }
    }
}
