import io.github.libfdx.build.LibExt

plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.tests"

base {
    archivesName.set("tests_web")
}

dependencies {
    implementation(project(":tests:core"))
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

tasks.register("test_webgl_js_build") {
    group = "application"
    description = "Builds the WebGL JavaScript test web application."
    dependsOn(":tests:platform:plugin:libfdx_web_js_webgl_build")
}

tasks.register("test_webgl_js_run") {
    group = "application"
    description = "Builds and serves the WebGL JavaScript test web application."
    dependsOn(":tests:platform:plugin:libfdx_web_js_webgl_run")
}

tasks.register("test_webgpu_js_build") {
    group = "application"
    description = "Builds the WebGPU JavaScript test web application."
    dependsOn(":tests:platform:plugin:libfdx_web_js_webgpu_build")
}

tasks.register("test_webgpu_js_run") {
    group = "application"
    description = "Builds and serves the WebGPU JavaScript test web application."
    dependsOn(":tests:platform:plugin:libfdx_web_js_webgpu_run")
}

tasks.register("test_webgl_wasm_build") {
    group = "application"
    description = "Builds the WebGL Wasm test web application."
    dependsOn(":tests:platform:plugin:libfdx_web_wasm_webgl_build")
}

tasks.register("test_webgl_wasm_run") {
    group = "application"
    description = "Builds and serves the WebGL Wasm test web application."
    dependsOn(":tests:platform:plugin:libfdx_web_wasm_webgl_run")
}
