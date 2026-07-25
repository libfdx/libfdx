
plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}


base {
    archivesName.set("sample_multiplayer_2d_webrtc_web")
}

dependencies {
    implementation(project(":samples:2d:multiplayer-webrtc:core"))
    implementation(libs.teavm.jso)
    implementation(libs.teavm.jso.apis)
    implementation(libs.teavm.jso.impl)
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_web:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:gl_web:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:wgpu_web:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:webrtc_web:${libs.versions.libfdxSnapshot.get()}")
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
