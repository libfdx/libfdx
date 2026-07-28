
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

val sampleProjectPath = project.path.substringBefore(":platform:")
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
        implementation("${libs.versions.libfdxGroup.get()}:webrtc_web:$libfdxDependencyVersion")
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
    dependsOn("$sampleProjectPath:platform:plugin:libfdx_web_js_webgl_build")
}

tasks.register("multiplayer_2d_webrtc_webgl_js_run") {
    group = "application"
    description = "Builds and serves the WebRTC multiplayer 2D WebGL JavaScript web application."
    dependsOn("$sampleProjectPath:platform:plugin:libfdx_web_js_webgl_run")
}

tasks.register("multiplayer_2d_webrtc_webgpu_js_build") {
    group = "application"
    description = "Builds the WebRTC multiplayer 2D WebGPU JavaScript web application."
    dependsOn("$sampleProjectPath:platform:plugin:libfdx_web_js_webgpu_build")
}

tasks.register("multiplayer_2d_webrtc_webgpu_js_run") {
    group = "application"
    description = "Builds and serves the WebRTC multiplayer 2D WebGPU JavaScript web application."
    dependsOn("$sampleProjectPath:platform:plugin:libfdx_web_js_webgpu_run")
}
