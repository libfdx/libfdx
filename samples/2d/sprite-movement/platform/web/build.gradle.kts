import io.github.libfdx.build.LibExt

plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.samples.g2d.spritemovement"

base {
    archivesName.set("sample_2d_sprite_movement_web")
}

dependencies {
    implementation(project(":samples:2d:sprite-movement:core"))
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

tasks.register("sprite_movement_webgl_js_build") {
    group = "application"
    description = "Builds the 2D Sprite Movement WebGL JavaScript web application."
    dependsOn(":samples:2d:sprite-movement:platform:plugin:libfdx_web_js_webgl_build")
}

tasks.register("sprite_movement_webgl_js_run") {
    group = "application"
    description = "Builds and serves the 2D Sprite Movement WebGL JavaScript web application."
    dependsOn(":samples:2d:sprite-movement:platform:plugin:libfdx_web_js_webgl_run")
}

tasks.register("sprite_movement_webgpu_js_build") {
    group = "application"
    description = "Builds the 2D Sprite Movement WebGPU JavaScript web application."
    dependsOn(":samples:2d:sprite-movement:platform:plugin:libfdx_web_js_webgpu_build")
}

tasks.register("sprite_movement_webgpu_js_run") {
    group = "application"
    description = "Builds and serves the 2D Sprite Movement WebGPU JavaScript web application."
    dependsOn(":samples:2d:sprite-movement:platform:plugin:libfdx_web_js_webgpu_run")
}

tasks.register("sprite_movement_webgl_wasm_build") {
    group = "application"
    description = "Builds the 2D Sprite Movement WebGL Wasm web application."
    dependsOn(":samples:2d:sprite-movement:platform:plugin:libfdx_web_wasm_webgl_build")
}

tasks.register("sprite_movement_webgl_wasm_run") {
    group = "application"
    description = "Builds and serves the 2D Sprite Movement WebGL Wasm web application."
    dependsOn(":samples:2d:sprite-movement:platform:plugin:libfdx_web_wasm_webgl_run")
}
