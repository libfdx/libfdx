
plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}


base {
    archivesName.set("sample_2d_sprite_movement_web")
}

dependencies {
    implementation(project(":samples:2d:sprite-movement:core"))
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
