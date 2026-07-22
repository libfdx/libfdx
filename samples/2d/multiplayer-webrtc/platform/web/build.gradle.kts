import io.github.libfdx.build.LibExt

plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.samples.multiplayer"

base {
    archivesName.set("sample_multiplayer_2d_webrtc_web")
}

dependencies {
    implementation(project(":samples:2d:multiplayer-webrtc:core"))
    implementation(libs.teavm.jso)
    implementation(libs.teavm.jso.apis)
    implementation(libs.teavm.jso.impl)
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_web:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:gl_web:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:wgpu_web:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:webrtc_web:${LibExt.fdxSnapshotVersion}")
    } else {
        implementation(project(":libfdx:backends:web"))
        implementation(project(":libfdx:extensions:graphics:gl:platform:web"))
        implementation(project(":libfdx:extensions:graphics:wgpu:platform:web"))
        implementation(project(":libfdx:extensions:net:webrtc:platform:web"))
    }
}

tasks.register("multiplayer_2d_webrtc_webgl_js_build") {
    group = "application"
    description = "Builds the WebRTC multiplayer 2D WebGL JavaScript web application."
    dependsOn(":samples:2d:multiplayer-webrtc:platform:plugin:libfdx_web_js_webgl_build")
}

tasks.register("multiplayer_2d_webrtc_webgl_js_run") {
    group = "application"
    description = "Builds and serves the WebRTC multiplayer 2D WebGL JavaScript web application."
    dependsOn(":samples:2d:multiplayer-webrtc:platform:plugin:libfdx_web_js_webgl_run")
}

tasks.register("multiplayer_2d_webrtc_webgpu_js_build") {
    group = "application"
    description = "Builds the WebRTC multiplayer 2D WebGPU JavaScript web application."
    dependsOn(":samples:2d:multiplayer-webrtc:platform:plugin:libfdx_web_js_webgpu_build")
}

tasks.register("multiplayer_2d_webrtc_webgpu_js_run") {
    group = "application"
    description = "Builds and serves the WebRTC multiplayer 2D WebGPU JavaScript web application."
    dependsOn(":samples:2d:multiplayer-webrtc:platform:plugin:libfdx_web_js_webgpu_run")
}
