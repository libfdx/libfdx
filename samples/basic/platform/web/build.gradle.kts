import io.github.libfdx.build.LibExt

plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.samples.basic"

base {
    archivesName.set("sample_basic_web")
}

dependencies {
    implementation(project(":samples:basic:core"))
    implementation(libs.teavm.jso)
    implementation(libs.teavm.jso.apis)
    implementation(libs.teavm.jso.impl)
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_web:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:gl_web:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:wgpu_web:${LibExt.fdxSnapshotVersion}")
    } else {
        implementation(project(":libfdx:backends:web"))
        implementation(project(":libfdx:extensions:graphics:gl:platform:web"))
        implementation(project(":libfdx:extensions:graphics:wgpu:platform:web"))
    }
}

tasks.register("basic_webgl_js_build") {
    group = "application"
    description = "Builds the basic WebGL JavaScript web application."
    dependsOn(":samples:basic:platform:plugin:libfdx_web_js_webgl_build")
}

tasks.register("basic_webgl_js_run") {
    group = "application"
    description = "Builds and serves the basic WebGL JavaScript web application."
    dependsOn(":samples:basic:platform:plugin:libfdx_web_js_webgl_run")
}

tasks.register("basic_webgpu_js_build") {
    group = "application"
    description = "Builds the basic WebGPU JavaScript web application."
    dependsOn(":samples:basic:platform:plugin:libfdx_web_js_webgpu_build")
}

tasks.register("basic_webgpu_js_run") {
    group = "application"
    description = "Builds and serves the basic WebGPU JavaScript web application."
    dependsOn(":samples:basic:platform:plugin:libfdx_web_js_webgpu_run")
}

tasks.register("basic_webgl_wasm_build") {
    group = "application"
    description = "Builds the basic WebGL Wasm web application."
    dependsOn(":samples:basic:platform:plugin:libfdx_web_wasm_webgl_build")
}

tasks.register("basic_webgl_wasm_run") {
    group = "application"
    description = "Builds and serves the basic WebGL Wasm web application."
    dependsOn(":samples:basic:platform:plugin:libfdx_web_wasm_webgl_run")
}
