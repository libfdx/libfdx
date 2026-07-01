plugins {
    id("io.github.libfdx")
}

libfdx {
    assets(rootProject.layout.projectDirectory.dir("assets"))

    js {
        mainClass.set("io.github.libfdx.samples.ecs.platformer.web.EcsPlatformerWebJsLauncher")
        htmlTitle.set("libfdx ECS Platformer - WebGL JS")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)

        target("webgl") {
            buildDescription.set("Builds the ECS platformer WebGL JavaScript web application.")
            runDescription.set("Builds and serves the ECS platformer WebGL JavaScript web application.")
        }

        target("webgpu") {
            defaultPath.set("/?graphics=webgpu")
            buildDescription.set("Builds the ECS platformer WebGPU JavaScript web application.")
            runDescription.set("Builds and serves the ECS platformer WebGPU JavaScript web application.")
        }
    }
    wasm {
        mainClass.set("io.github.libfdx.samples.ecs.platformer.web.EcsPlatformerWebWasmLauncher")
        htmlTitle.set("libfdx ECS Platformer - WebGL Wasm")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)

        target("webgl") {
            buildDescription.set("Builds the ECS platformer WebGL Wasm web application.")
            runDescription.set("Builds and serves the ECS platformer WebGL Wasm web application.")
        }

        target("webgpu") {
            defaultPath.set("/?graphics=webgpu")
            buildDescription.set("Builds the ECS platformer WebGPU Wasm web application.")
            runDescription.set("Builds and serves the ECS platformer WebGPU Wasm web application.")
        }
    }
}
