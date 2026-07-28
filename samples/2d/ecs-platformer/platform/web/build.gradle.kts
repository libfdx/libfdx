plugins {
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("sample_ecs_platformer_web")
}

val sampleProjectPath = project.path.substringBefore(":platform:")
val sampleRoot = layout.projectDirectory.dir("../..")
val libfdxDependencyVersion =
    gradle.extensions.extraProperties.get("libfdxDependencyVersion") as String

dependencies {
    implementation(project("$sampleProjectPath:core"))
    implementation(libs.teavm.jso)
    implementation(libs.teavm.jso.apis)
    implementation(libs.teavm.jso.impl)
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_web:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:gl_web:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:wgpu_web:$libfdxDependencyVersion")
    } else {
        implementation(project(":libfdx:backends:web"))
        implementation(project(":libfdx:extensions:graphics:gl:platform:web"))
        implementation(project(":libfdx:extensions:graphics:wgpu:platform:web"))
    }
}

libfdx {
    assets(sampleRoot.dir("assets"))

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
    }
}
