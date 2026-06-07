import io.github.libfdx.build.LibExt

plugins {
    id("java")
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("project_generator_web")
}

group = "${LibExt.fdxGroup}.tools.projectgenerator"

dependencies {
    implementation(project(":libfdx:tools:project-generator:core"))
    implementation(project(":libfdx:tools:project-generator:ui"))
    implementation(project(":libfdx:backends:web"))
    implementation(project(":libfdx:extensions:graphics:gl:platform:web"))
    implementation(project(":libfdx:extensions:graphics:wgpu:platform:web"))
    implementation(libs.teavm.jso)
}

libfdx {
    js {
        mainClass.set("io.github.libfdx.tools.project.generator.web.ProjectGeneratorWebJsLauncher")
        htmlTitle.set("libfdx Project Generator - WebGL JS")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)
    }
    wasm {
        mainClass.set("io.github.libfdx.tools.project.generator.web.ProjectGeneratorWebWasmLauncher")
        htmlTitle.set("libfdx Project Generator - WebGL Wasm")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)
    }
}

val jsWebappDir = layout.buildDirectory.dir("dist/web-js/webapp")
val wasmWebappDir = layout.buildDirectory.dir("dist/web-wasm/webapp")

tasks.register("build_web_js") {
    group = "application"
    description = "Builds the libfdx project generator WebGL JavaScript web application."
    dependsOn("libfdx_web_js_build")
}

tasks.register("build_web_wasm") {
    group = "application"
    description = "Builds the libfdx project generator WebGL Wasm web application."
    dependsOn("libfdx_web_wasm_build")
}

tasks.register<io.github.libfdx.gradle.LibfdxRunWebTask>("run_web_js") {
    group = "application"
    description = "Builds and serves the libfdx project generator WebGL JavaScript web application."
    dependsOn("build_web_js")
    webappDir.set(jsWebappDir)
    port.set(libfdx.js.serverPort)
    defaultPath.set("/")
}

tasks.register<io.github.libfdx.gradle.LibfdxRunWebTask>("run_web_wasm") {
    group = "application"
    description = "Builds and serves the libfdx project generator WebGL Wasm web application."
    dependsOn("build_web_wasm")
    webappDir.set(wasmWebappDir)
    port.set(libfdx.wasm.serverPort)
    defaultPath.set("/")
}

tasks.register<JavaExec>("test_archive_project") {
    group = "verification"
    description = "Runs the web project archive smoke checks."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.libfdx.tools.project.generator.web.WebProjectArchiveSmokeTest")
}

tasks.named<Test>("test") {
    enabled = false
}

tasks.named("check") {
    dependsOn("test_archive_project")
}
