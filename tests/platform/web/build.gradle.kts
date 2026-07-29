plugins {
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("tests_web")
}

dependencies {
    implementation(project(":tests:core"))
    implementation(libs.teavm.jso)
    implementation(libs.teavm.jso.apis)
    implementation(libs.teavm.jso.impl)
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_web:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:gl_web:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:wgpu_web:${libs.versions.libfdxSnapshot.get()}")
    } else {
        implementation(project(":libfdx:backends:web"))
        implementation(project(":libfdx:extensions:graphics:gl:platform:web"))
        implementation(project(":libfdx:extensions:graphics:wgpu:platform:web"))
    }
}

libfdx {
    assets(rootProject.layout.projectDirectory.dir("tests/assets"))

    js {
        mainClass.set("io.github.libfdx.tests.web.WebTestJsLauncher")
        htmlTitle.set("libfdx Tests - WebGL JS")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)

        target("webgl") {
            buildDescription.set("Builds the WebGL JavaScript test web application.")
            runDescription.set("Builds and serves the WebGL JavaScript test web application.")
        }
        target("webgpu") {
            defaultPath.set("/?graphics=webgpu")
            buildDescription.set("Builds the WebGPU JavaScript test web application.")
            runDescription.set("Builds and serves the WebGPU JavaScript test web application.")
        }
    }
    wasm {
        mainClass.set("io.github.libfdx.tests.web.WebTestWasmLauncher")
        htmlTitle.set("libfdx Tests - WebGL Wasm")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)

        target("webgl") {
            buildDescription.set("Builds the WebGL Wasm test web application.")
            runDescription.set("Builds and serves the WebGL Wasm test web application.")
        }
    }
}
