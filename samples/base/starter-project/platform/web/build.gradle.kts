plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("sample_base_starter_project_web")
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
    } else {
        implementation(project(":libfdx:backends:web"))
        implementation(project(":libfdx:extensions:graphics:gl:platform:web"))
        implementation(project(":libfdx:extensions:graphics:wgpu:platform:web"))
    }
}

tasks.register("starter_project_webgl_js_build") {
    group = "application"
    description = "Builds the Starter Project WebGL JavaScript web application."
    dependsOn("$sampleProjectPath:platform:plugin:libfdx_web_js_webgl_build")
}

tasks.register("starter_project_webgl_js_run") {
    group = "application"
    description = "Builds and serves the Starter Project WebGL JavaScript web application."
    dependsOn("$sampleProjectPath:platform:plugin:libfdx_web_js_webgl_run")
}

tasks.register("starter_project_webgpu_js_build") {
    group = "application"
    description = "Builds the Starter Project WebGPU JavaScript web application."
    dependsOn("$sampleProjectPath:platform:plugin:libfdx_web_js_webgpu_build")
}

tasks.register("starter_project_webgpu_js_run") {
    group = "application"
    description = "Builds and serves the Starter Project WebGPU JavaScript web application."
    dependsOn("$sampleProjectPath:platform:plugin:libfdx_web_js_webgpu_run")
}

tasks.register("starter_project_webgl_wasm_build") {
    group = "application"
    description = "Builds the Starter Project WebGL WebAssembly web application."
    dependsOn("$sampleProjectPath:platform:plugin:libfdx_web_wasm_webgl_build")
}

tasks.register("starter_project_webgl_wasm_run") {
    group = "application"
    description = "Builds and serves the Starter Project WebGL WebAssembly web application."
    dependsOn("$sampleProjectPath:platform:plugin:libfdx_web_wasm_webgl_run")
}
