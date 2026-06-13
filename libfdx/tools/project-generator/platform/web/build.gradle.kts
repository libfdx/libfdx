import io.github.libfdx.build.LibExt

import com.sun.net.httpserver.HttpServer
import org.gradle.api.file.FileCollection
import java.lang.reflect.InvocationTargetException
import java.net.InetSocketAddress
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.concurrent.CountDownLatch

plugins {
    id("java")
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
    implementation(libs.teavm.jso.apis)
    implementation(libs.teavm.jso.impl)
}

val jsWebappDir = layout.buildDirectory.dir("dist/web-js/webapp")
val wasmWebappDir = layout.buildDirectory.dir("dist/web-wasm/webapp")
val builderClasspath = sourceSets["main"].runtimeClasspath
val webPort = providers.gradleProperty("libfdx.web.port").map(String::toInt).orElse(8080)

fun registerWebBuild(taskName: String, descriptionText: String, target: String, mainClassName: String,
        title: String, outputPath: Provider<Directory>, optimization: String) {
    tasks.register(taskName) {
        group = "application"
        description = descriptionText
        if (!LibExt.usePublishedLibfdx) {
            dependsOn(":libfdx:runtime:fdx:platform:web:generate_runtime_fdx_web_native")
        }
        dependsOn(builderClasspath)
        inputs.files(builderClasspath)
        outputs.dir(outputPath)
        doLast {
            runWebBuilder(builderClasspath, target, mainClassName, title, outputPath.get().asFile, optimization)
        }
    }
}

fun registerWebRun(taskName: String, descriptionText: String, buildTaskName: String,
        webappDir: Provider<Directory>, defaultPath: String) {
    tasks.register(taskName) {
        group = "application"
        description = descriptionText
        dependsOn(buildTaskName)
        inputs.dir(webappDir)
        doLast {
            runWebServer(webappDir.get().asFile, webPort.get(), defaultPath)
        }
    }
}

registerWebBuild("project_generator_webgl_js_build",
        "Builds the libfdx project generator WebGL JavaScript web application.",
        "js",
        "io.github.libfdx.tools.project.generator.web.ProjectGeneratorWebJsLauncher",
        "libfdx Project Generator - WebGL JS",
        jsWebappDir,
        "BALANCED")

registerWebBuild("project_generator_webgl_wasm_build",
        "Builds the libfdx project generator WebGL Wasm web application.",
        "wasm",
        "io.github.libfdx.tools.project.generator.web.ProjectGeneratorWebWasmLauncher",
        "libfdx Project Generator - WebGL Wasm",
        wasmWebappDir,
        "AGGRESSIVE")

tasks.register("project_generator_webgpu_js_build") {
    group = "application"
    description = "Builds the libfdx project generator WebGPU JavaScript web application."
    dependsOn("project_generator_webgl_js_build")
    configureWebGpuPage("dist/web-js/webapp", "libfdx Project Generator - WebGPU JS")
}

tasks.register("project_generator_webgpu_wasm_build") {
    group = "application"
    description = "Builds the libfdx project generator WebGPU Wasm web application."
    dependsOn("project_generator_webgl_wasm_build")
    configureWebGpuPage("dist/web-wasm/webapp", "libfdx Project Generator - WebGPU Wasm")
}

registerWebRun("project_generator_webgl_js_run",
        "Builds and serves the libfdx project generator WebGL JavaScript web application.",
        "project_generator_webgl_js_build", jsWebappDir, "/")

registerWebRun("project_generator_webgl_wasm_run",
        "Builds and serves the libfdx project generator WebGL Wasm web application.",
        "project_generator_webgl_wasm_build", wasmWebappDir, "/")

registerWebRun("project_generator_webgpu_js_run",
        "Builds and serves the libfdx project generator WebGPU JavaScript web application.",
        "project_generator_webgpu_js_build", jsWebappDir, "/webgpu.html")

registerWebRun("project_generator_webgpu_wasm_run",
        "Builds and serves the libfdx project generator WebGPU Wasm web application.",
        "project_generator_webgpu_wasm_build", wasmWebappDir, "/webgpu.html")

fun runWebBuilder(classpath: FileCollection, target: String, mainClassName: String, title: String, outputDir: File,
        optimization: String, assets: List<File> = emptyList()) {
    withBuilderClassLoader(classpath) { classLoader ->
        val builderClass = classLoader.loadClass("io.github.libfdx.backend.web.WebBuilder")
        var builder = builderClass.getMethod(if (target == "wasm") "wasm" else "javascript").invoke(null)
        val paths = classpath.files.map { it.toPath() }
        builder = invokeBuilder(builder, "classpath", listOf(Collection::class.java), listOf(paths))
        builder = invokeBuilder(builder, "runtimeClasspath", listOf(Collection::class.java), listOf(paths))
        builder = invokeBuilder(builder, "webappDirectory", listOf(Path::class.java), listOf(outputDir.toPath()))
        builder = invokeBuilder(builder, "mainClass", listOf(String::class.java), listOf(mainClassName))
        builder = invokeBuilder(builder, "title", listOf(String::class.java), listOf(title))
        builder = invokeBuilder(builder, "canvasId", listOf(String::class.java), listOf("libfdx-canvas"))
        builder = invokeBuilder(builder, "size", listOf(Integer.TYPE, Integer.TYPE), listOf(0, 0))
        val optimizationClass = classLoader.loadClass("io.github.libfdx.backend.cshared.TeaVMOptimization")
        builder = invokeBuilder(builder, "optimization", listOf(optimizationClass), listOf(enumValue(optimizationClass, optimization)))
        if (assets.isNotEmpty()) {
            builder = invokeBuilder(builder, "assets", listOf(Collection::class.java), listOf(assets.map { it.toPath() }))
        }
        invokeBuilder(builder, "build", emptyList(), emptyList())
    }
}

fun withBuilderClassLoader(classpath: FileCollection, action: (ClassLoader) -> Unit) {
    val urls = classpath.files.map { it.toURI().toURL() }.toTypedArray()
    URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { classLoader ->
        val previous = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = classLoader
        try {
            action(classLoader)
        }
        finally {
            Thread.currentThread().contextClassLoader = previous
        }
    }
}

fun invokeBuilder(target: Any, methodName: String, parameterTypes: List<Class<*>>, args: List<Any>): Any {
    try {
        return target.javaClass.getMethod(methodName, *parameterTypes.toTypedArray())
                .invoke(target, *args.toTypedArray()) ?: target
    }
    catch (error: InvocationTargetException) {
        val cause = error.targetException
        if (cause is RuntimeException) {
            throw cause
        }
        if (cause is Error) {
            throw cause
        }
        throw GradleException("Builder method '$methodName' failed.", cause)
    }
}

fun enumValue(enumClass: Class<*>, value: String): Any {
    return Enum::class.java.getMethod("valueOf", Class::class.java, String::class.java)
            .invoke(null, enumClass, value)
}

fun runWebServer(rootDirectory: File, port: Int, defaultPath: String) {
    val root = rootDirectory.canonicalFile
    val server = HttpServer.create(InetSocketAddress(port), 0)
    server.createContext("/") { exchange ->
        serveWebFile(root, exchange)
    }
    server.executor = null
    val shutdownHook = Thread { server.stop(0) }
    Runtime.getRuntime().addShutdownHook(shutdownHook)
    try {
        server.start()
        val path = if (defaultPath.startsWith("/")) defaultPath else "/$defaultPath"
        logger.lifecycle("Serving ${root.absolutePath} at http://localhost:$port$path")
        CountDownLatch(1).await()
    }
    finally {
        server.stop(0)
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook)
        }
        catch (_: IllegalStateException) {
        }
    }
}

fun serveWebFile(root: File, exchange: com.sun.net.httpserver.HttpExchange) {
    val rawPath = exchange.requestURI.path.trimStart('/')
    val requested = File(root, if (rawPath.isEmpty()) "index.html" else rawPath).canonicalFile
    val file = if (requested.isDirectory) File(requested, "index.html") else requested
    if (!file.toPath().startsWith(root.toPath()) || !file.isFile) {
        exchange.sendResponseHeaders(404, -1)
        exchange.close()
        return
    }
    val bytes = file.readBytes()
    exchange.responseHeaders.add("Content-Type", webContentType(file.name))
    exchange.sendResponseHeaders(200, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}

fun webContentType(name: String): String {
    return when {
        name.endsWith(".html") -> "text/html; charset=utf-8"
        name.endsWith(".js") -> "text/javascript; charset=utf-8"
        name.endsWith(".wasm") -> "application/wasm"
        name.endsWith(".json") || name.endsWith(".gltf") -> "application/json; charset=utf-8"
        name.endsWith(".glb") -> "model/gltf-binary"
        name.endsWith(".bin") -> "application/octet-stream"
        name.endsWith(".txt") -> "text/plain; charset=utf-8"
        name.endsWith(".css") -> "text/css; charset=utf-8"
        name.endsWith(".png") -> "image/png"
        name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
        else -> "application/octet-stream"
    }
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
