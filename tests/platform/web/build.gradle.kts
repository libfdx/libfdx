import org.teavm.gradle.api.OptimizationLevel

plugins {
    id("java")
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "io.github.libfdx.tests"

base {
    archivesName.set("tests_web")
}

dependencies {
    implementation(project(":tests:core"))
    implementation(project(":libfdx:backends:web"))
    implementation(project(":libfdx:extensions:graphics:gl:platform:web"))
    implementation(project(":libfdx:extensions:graphics:wgpu:platform:web"))
}

libfdx {
    assets(rootProject.file("tests/assets"))
    js {
        mainClass.set("io.github.libfdx.tests.web.WebTestJsLauncher")
        htmlTitle.set("libfdx Tests - Web JS")
        canvasId.set("libfdx-canvas")
        optimization.set(OptimizationLevel.AGGRESSIVE)
        htmlWidth.set(0)
        htmlHeight.set(0)
    }
    wasm {
        mainClass.set("io.github.libfdx.tests.web.WebTestWasmLauncher")
        htmlTitle.set("libfdx Tests - Web Wasm")
        canvasId.set("libfdx-canvas")
        optimization.set(OptimizationLevel.BALANCED)
        htmlWidth.set(0)
        htmlHeight.set(0)
    }
}

val jsWebappDir = layout.buildDirectory.dir("dist/web-js/webapp")
val wasmWebappDir = layout.buildDirectory.dir("dist/web-wasm/webapp")
val webGpuJsPage = registerWebGpuPage(
    "test_webgpu_js_page",
    "libfdx_web_js_build",
    "dist/web-js/webapp",
    "libfdx Tests - WebGPU JS"
)
val webGpuWasmPage = registerWebGpuPage(
    "test_webgpu_wasm_page",
    "libfdx_web_wasm_build",
    "dist/web-wasm/webapp",
    "libfdx Tests - WebGPU Wasm"
)

tasks.register("test_webgl_js_build") {
    group = "application"
    description = "Builds the WebGL JavaScript test web application."
    dependsOn("libfdx_web_js_build")
}

tasks.register("test_webgl_wasm_build") {
    group = "application"
    description = "Builds the WebGL Wasm test web application."
    dependsOn("libfdx_web_wasm_build")
}

tasks.register("test_webgpu_js_build") {
    group = "application"
    description = "Builds the WebGPU JavaScript test web application."
    dependsOn(webGpuJsPage)
}

tasks.register("test_webgpu_wasm_build") {
    group = "application"
    description = "Builds the WebGPU Wasm test web application."
    dependsOn(webGpuWasmPage)
}

tasks.register<io.github.libfdx.gradle.LibfdxRunWebTask>("test_webgl_js_run") {
    group = "application"
    description = "Builds and serves the WebGL JavaScript test web application."
    dependsOn("test_webgl_js_build")
    webappDir.set(jsWebappDir)
    port.set(libfdx.js.serverPort)
    defaultPath.set("/")
}

tasks.register<io.github.libfdx.gradle.LibfdxRunWebTask>("test_webgl_wasm_run") {
    group = "application"
    description = "Builds and serves the WebGL Wasm test web application."
    dependsOn("test_webgl_wasm_build")
    webappDir.set(wasmWebappDir)
    port.set(libfdx.wasm.serverPort)
    defaultPath.set("/")
}

tasks.register<io.github.libfdx.gradle.LibfdxRunWebTask>("test_webgpu_js_run") {
    group = "application"
    description = "Builds and serves the WebGPU JavaScript test web application."
    dependsOn("test_webgpu_js_build")
    webappDir.set(jsWebappDir)
    port.set(libfdx.js.serverPort)
    defaultPath.set("/webgpu.html")
}

tasks.register<io.github.libfdx.gradle.LibfdxRunWebTask>("test_webgpu_wasm_run") {
    group = "application"
    description = "Builds and serves the WebGPU Wasm test web application."
    dependsOn("test_webgpu_wasm_build")
    webappDir.set(wasmWebappDir)
    port.set(libfdx.wasm.serverPort)
    defaultPath.set("/webgpu.html")
}

fun registerWebGpuPage(taskName: String, buildTaskName: String, webappPath: String, title: String) = tasks.register(taskName) {
    dependsOn(buildTaskName)
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
