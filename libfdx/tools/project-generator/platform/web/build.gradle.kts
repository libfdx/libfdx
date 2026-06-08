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

tasks.register("project_generator_webgl_js_build") {
    group = "application"
    description = "Builds the libfdx project generator WebGL JavaScript web application."
    dependsOn("libfdx_web_js_build")
}

tasks.register("project_generator_webgl_wasm_build") {
    group = "application"
    description = "Builds the libfdx project generator WebGL Wasm web application."
    dependsOn("libfdx_web_wasm_build")
}

tasks.register("project_generator_webgpu_js_build") {
    group = "application"
    description = "Builds the libfdx project generator WebGPU JavaScript web application."
    dependsOn("libfdx_web_js_build")
    configureWebGpuPage("dist/web-js/webapp", "libfdx Project Generator - WebGPU JS")
}

tasks.register("project_generator_webgpu_wasm_build") {
    group = "application"
    description = "Builds the libfdx project generator WebGPU Wasm web application."
    dependsOn("libfdx_web_wasm_build")
    configureWebGpuPage("dist/web-wasm/webapp", "libfdx Project Generator - WebGPU Wasm")
}

tasks.register<io.github.libfdx.gradle.LibfdxRunWebTask>("project_generator_webgl_js_run") {
    group = "application"
    description = "Builds and serves the libfdx project generator WebGL JavaScript web application."
    dependsOn("project_generator_webgl_js_build")
    webappDir.set(jsWebappDir)
    port.set(libfdx.js.serverPort)
    defaultPath.set("/")
}

tasks.register<io.github.libfdx.gradle.LibfdxRunWebTask>("project_generator_webgl_wasm_run") {
    group = "application"
    description = "Builds and serves the libfdx project generator WebGL Wasm web application."
    dependsOn("project_generator_webgl_wasm_build")
    webappDir.set(wasmWebappDir)
    port.set(libfdx.wasm.serverPort)
    defaultPath.set("/")
}

tasks.register<io.github.libfdx.gradle.LibfdxRunWebTask>("project_generator_webgpu_js_run") {
    group = "application"
    description = "Builds and serves the libfdx project generator WebGPU JavaScript web application."
    dependsOn("project_generator_webgpu_js_build")
    webappDir.set(jsWebappDir)
    port.set(libfdx.js.serverPort)
    defaultPath.set("/webgpu.html")
}

tasks.register<io.github.libfdx.gradle.LibfdxRunWebTask>("project_generator_webgpu_wasm_run") {
    group = "application"
    description = "Builds and serves the libfdx project generator WebGPU Wasm web application."
    dependsOn("project_generator_webgpu_wasm_build")
    webappDir.set(wasmWebappDir)
    port.set(libfdx.wasm.serverPort)
    defaultPath.set("/webgpu.html")
}

fun Task.configureWebGpuPage(webappPath: String, title: String) {
    val webappDir = layout.buildDirectory.dir(webappPath)
    val indexFile = webappDir.map { it.file("index.html") }
    val outputFile = webappDir.map { it.file("webgpu.html") }
    inputs.file(indexFile)
    outputs.file(outputFile)
    doLast {
        writeWebGpuPage(indexFile.get().asFile, outputFile.get().asFile, title)
    }
}

fun writeWebGpuPage(indexFile: File, outputFile: File, title: String) {
    val source = indexFile.readText()
    val withTitle = source.replace(Regex("<title>.*</title>"), "<title>$title</title>")
    val rewritten = when {
        withTitle.contains("main();") -> withTitle.replace(
            "main();",
            "main([\"--graphics=webgpu\"]);"
        )
        withTitle.contains("teavm.exports.main([]);") -> withTitle.replace(
            "teavm.exports.main([]);",
            "teavm.exports.main([\"--graphics=webgpu\"]);"
        )
        else -> throw org.gradle.api.GradleException(
            "Could not create WebGPU launch page from ${indexFile.absolutePath}"
        )
    }
    outputFile.writeText(rewritten)
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
