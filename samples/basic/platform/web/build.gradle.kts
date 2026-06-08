import io.github.libfdx.build.LibExt

plugins {
    id("java")
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.samples.basic"


base {
    archivesName.set("sample_basic_web")
}

dependencies {
    implementation(project(":samples:basic:core"))
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_web:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:gl_web:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:wgpu_web:${LibExt.publishedLibfdxVersion}")
    } else {
        implementation(project(":libfdx:backends:web"))
        implementation(project(":libfdx:extensions:graphics:gl:platform:web"))
        implementation(project(":libfdx:extensions:graphics:wgpu:platform:web"))
    }
}

libfdx {
    js {
        mainClass.set("io.github.libfdx.samples.basic.web.BasicWebJsLauncher")
        htmlTitle.set("libfdx Basic - WebGL JS")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)
    }
    wasm {
        mainClass.set("io.github.libfdx.samples.basic.web.BasicWebWasmLauncher")
        htmlTitle.set("libfdx Basic - WebGL Wasm")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)
    }
}

val jsWebappDir = layout.buildDirectory.dir("dist/web-js/webapp")
val wasmWebappDir = layout.buildDirectory.dir("dist/web-wasm/webapp")

tasks.register("basic_webgl_js_build") {
    group = "application"
    description = "Builds the WebGL JavaScript basic sample web application."
    dependsOn("libfdx_web_js_build")
}

tasks.register("basic_webgl_wasm_build") {
    group = "application"
    description = "Builds the WebGL Wasm basic sample web application."
    dependsOn("libfdx_web_wasm_build")
}

tasks.register("basic_webgpu_js_build") {
    group = "application"
    description = "Builds the WebGPU JavaScript basic sample web application."
    dependsOn("libfdx_web_js_build")
    configureWebGpuPage("dist/web-js/webapp", "libfdx Basic - WebGPU JS")
}

tasks.register("basic_webgpu_wasm_build") {
    group = "application"
    description = "Builds the WebGPU Wasm basic sample web application."
    dependsOn("libfdx_web_wasm_build")
    configureWebGpuPage("dist/web-wasm/webapp", "libfdx Basic - WebGPU Wasm")
}

tasks.register<io.github.libfdx.gradle.LibfdxRunWebTask>("basic_webgl_js_run") {
    group = "application"
    description = "Builds and serves the WebGL JavaScript basic sample web application."
    dependsOn("basic_webgl_js_build")
    webappDir.set(jsWebappDir)
    port.set(libfdx.js.serverPort)
    defaultPath.set("/")
}

tasks.register<io.github.libfdx.gradle.LibfdxRunWebTask>("basic_webgl_wasm_run") {
    group = "application"
    description = "Builds and serves the WebGL Wasm basic sample web application."
    dependsOn("basic_webgl_wasm_build")
    webappDir.set(wasmWebappDir)
    port.set(libfdx.wasm.serverPort)
    defaultPath.set("/")
}

tasks.register<io.github.libfdx.gradle.LibfdxRunWebTask>("basic_webgpu_js_run") {
    group = "application"
    description = "Builds and serves the WebGPU JavaScript basic sample web application."
    dependsOn("basic_webgpu_js_build")
    webappDir.set(jsWebappDir)
    port.set(libfdx.js.serverPort)
    defaultPath.set("/webgpu.html")
}

tasks.register<io.github.libfdx.gradle.LibfdxRunWebTask>("basic_webgpu_wasm_run") {
    group = "application"
    description = "Builds and serves the WebGPU Wasm basic sample web application."
    dependsOn("basic_webgpu_wasm_build")
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
