import io.github.libfdx.build.LibExt

plugins {
    id("base")
}

LibExt.configure(rootProject.projectDir)

allprojects {
    group = LibExt.fdxGroup
    version = LibExt.fdxVersion

    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
    }

    configurations.configureEach {
        // Check for updates every sync
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }
}

fun runtimeFdxHostNativeTaskPath(): String {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("windows") -> ":libfdx:runtime:fdx:platform:desktop:generate_runtime_fdx_windows_native"
        os.contains("linux") -> ":libfdx:runtime:fdx:platform:desktop:generate_runtime_fdx_linux_native"
        os.contains("mac") || os.contains("darwin") -> ":libfdx:runtime:fdx:platform:desktop:generate_runtime_fdx_macos_native"
        else -> throw GradleException("Unsupported host OS for runtime fdx native artifacts: ${System.getProperty("os.name")}")
    }
}

val nativeArtifactTaskPaths = listOf(
    runtimeFdxHostNativeTaskPath(),
    ":libfdx:runtime:fdx:platform:web:generate_runtime_fdx_web_native",
    ":libfdx:runtime:fdx:platform:android:assembleRelease",
    ":libfdx:backends:android:assembleRelease",
    ":libfdx:extensions:graphics:vulkan:platform:android_jni:assembleRelease",
    ":libfdx:extensions:graphics:wgpu:platform:android_jni:assembleRelease"
)

tasks.register("libfdx_build_native_artifacts") {
    group = "libfdx native"
    description = "Builds native artifacts using prebuilt fdx-natives dependency packages."
    dependsOn(nativeArtifactTaskPaths)
}

tasks.register("libfdx_build_native_artifacts_prebuilt") {
    group = "libfdx native"
    description = "Builds native artifacts using prebuilt fdx-natives dependency packages."
    dependsOn(nativeArtifactTaskPaths)
}

tasks.register("libfdx_build_windows_native_artifact_prebuilt") {
    group = "libfdx native"
    description = "Builds the Windows runtime fdx native artifact using prebuilt fdx-natives dependency packages."
    dependsOn(":libfdx:runtime:fdx:platform:desktop:generate_runtime_fdx_windows_native_prebuilt")
}

tasks.register("libfdx_build_linux_native_artifact_prebuilt") {
    group = "libfdx native"
    description = "Builds the Linux runtime fdx native artifact using prebuilt fdx-natives dependency packages."
    dependsOn(":libfdx:runtime:fdx:platform:desktop:generate_runtime_fdx_linux_native_prebuilt")
}

tasks.register("libfdx_build_macos_native_artifact_prebuilt") {
    group = "libfdx native"
    description = "Builds the macOS runtime fdx native artifact for the current runner using prebuilt fdx-natives dependency packages."
    dependsOn(":libfdx:runtime:fdx:platform:desktop:generate_runtime_fdx_macos_native_prebuilt")
}

tasks.register("libfdx_build_web_native_artifacts_prebuilt") {
    group = "libfdx native"
    description = "Builds web runtime fdx native artifacts using prebuilt fdx-natives dependency packages."
    dependsOn(":libfdx:runtime:fdx:platform:web:generate_runtime_fdx_web_native_prebuilt")
}

tasks.register("libfdx_build_android_native_artifacts_prebuilt") {
    group = "libfdx native"
    description = "Builds Android native/library artifacts using prebuilt fdx-natives dependency packages for runtime fdx."
    dependsOn(
        ":libfdx:runtime:fdx:platform:android:assemble_runtime_fdx_android_release_prebuilt",
        ":libfdx:backends:android:assembleRelease",
        ":libfdx:extensions:graphics:vulkan:platform:android_jni:assembleRelease",
        ":libfdx:extensions:graphics:wgpu:platform:android_jni:assembleRelease"
    )
}

tasks.register("printFdxVersion") {
    group = "help"
    description = "Prints the libFDX version configured by LibExt."
    doLast {
        println(LibExt.fdxVersion)
    }
}

tasks.register("benchmark_desktop") {
    group = "benchmark"
    description = "Runs the full desktop JVM benchmark suite and generates Markdown reports."
    dependsOn(":benchmark:platform:desktop:benchmark_desktop")
}

tasks.register("benchmark_desktop_c_debug") {
    group = "benchmark"
    description = "Runs the full desktop_c Debug benchmark suite and generates Markdown reports."
    dependsOn(":benchmark:platform:desktop_c:benchmark_desktop_c_debug")
}

tasks.register("benchmark_desktop_c_release") {
    group = "benchmark"
    description = "Runs the full desktop_c Release benchmark suite and generates Markdown reports."
    dependsOn(":benchmark:platform:desktop_c:benchmark_desktop_c_release")
}

val pagesStagingDir = layout.buildDirectory.dir("pages")

tasks.register<Sync>("stage_pages") {
    group = "publishing"
    description = "Builds and stages hosted web outputs under build/pages."
    into(pagesStagingDir)
    pagesWebapp(
        projectPath = ":libfdx:tools:project-generator:platform:web",
        buildTaskName = "project_generator_webgl_js_build",
        webappPath = "dist/web-js/webapp",
        pagesPath = "project-generator/webgl-js"
    )
    pagesWebapp(
        projectPath = ":libfdx:tools:project-generator:platform:web",
        buildTaskName = "project_generator_webgl_wasm_build",
        webappPath = "dist/web-wasm/webapp",
        pagesPath = "project-generator/webgl-wasm"
    )
    pagesWebapp(
        projectPath = ":libfdx:tools:project-generator:platform:web",
        buildTaskName = "project_generator_webgpu_js_build",
        webappPath = "dist/web-js/webapp",
        pagesPath = "project-generator/webgpu-js",
        indexFile = "webgpu.html"
    )
    pagesWebapp(
        projectPath = ":libfdx:tools:project-generator:platform:web",
        buildTaskName = "project_generator_webgpu_wasm_build",
        webappPath = "dist/web-wasm/webapp",
        pagesPath = "project-generator/webgpu-wasm",
        indexFile = "webgpu.html"
    )
    pagesWebapp(
        projectPath = ":tests:platform:web",
        buildTaskName = "test_webgl_js_build",
        webappPath = "dist/web-js/webapp",
        pagesPath = "tests/webgl-js"
    )
    pagesWebapp(
        projectPath = ":tests:platform:web",
        buildTaskName = "test_webgl_wasm_build",
        webappPath = "dist/web-wasm/webapp",
        pagesPath = "tests/webgl-wasm"
    )
    pagesWebapp(
        projectPath = ":tests:platform:web",
        buildTaskName = "test_webgpu_js_build",
        webappPath = "dist/web-js/webapp",
        pagesPath = "tests/webgpu-js",
        indexFile = "webgpu.html"
    )
    pagesWebapp(
        projectPath = ":tests:platform:web",
        buildTaskName = "test_webgpu_wasm_build",
        webappPath = "dist/web-wasm/webapp",
        pagesPath = "tests/webgpu-wasm",
        indexFile = "webgpu.html"
    )
    pagesWebapp(
        projectPath = ":samples:basic:platform:web",
        buildTaskName = "basic_webgl_js_build",
        webappPath = "dist/web-js/webapp",
        pagesPath = "samples/basic/webgl-js"
    )
    pagesWebapp(
        projectPath = ":samples:basic:platform:web",
        buildTaskName = "basic_webgl_wasm_build",
        webappPath = "dist/web-wasm/webapp",
        pagesPath = "samples/basic/webgl-wasm"
    )
    pagesWebapp(
        projectPath = ":samples:basic:platform:web",
        buildTaskName = "basic_webgpu_js_build",
        webappPath = "dist/web-js/webapp",
        pagesPath = "samples/basic/webgpu-js",
        indexFile = "webgpu.html"
    )
    pagesWebapp(
        projectPath = ":samples:basic:platform:web",
        buildTaskName = "basic_webgpu_wasm_build",
        webappPath = "dist/web-wasm/webapp",
        pagesPath = "samples/basic/webgpu-wasm",
        indexFile = "webgpu.html"
    )
    doLast {
        val root = pagesStagingDir.get().asFile
        writeSelectorPage(
            root.resolve("project-generator/index.html"),
            "Project Generator",
            listOf(
                "WebGL JS" to "webgl-js/",
                "WebGL Wasm" to "webgl-wasm/",
                "WebGPU JS" to "webgpu-js/",
                "WebGPU Wasm" to "webgpu-wasm/"
            )
        )
        writeSelectorPage(
            root.resolve("tests/index.html"),
            "Tests",
            listOf(
                "WebGL JS" to "webgl-js/",
                "WebGL Wasm" to "webgl-wasm/",
                "WebGPU JS" to "webgpu-js/",
                "WebGPU Wasm" to "webgpu-wasm/"
            )
        )
        writeSelectorPage(
            root.resolve("samples/basic/index.html"),
            "Basic Sample",
            listOf(
                "WebGL JS" to "webgl-js/",
                "WebGL Wasm" to "webgl-wasm/",
                "WebGPU JS" to "webgpu-js/",
                "WebGPU Wasm" to "webgpu-wasm/"
            )
        )
    }
}

fun Sync.pagesWebapp(
    projectPath: String,
    buildTaskName: String,
    webappPath: String,
    pagesPath: String,
    indexFile: String = "index.html"
) {
    dependsOn("$projectPath:$buildTaskName")
    from(project(projectPath).layout.buildDirectory.dir(webappPath)) {
        if (indexFile == "index.html") {
            exclude("webgpu.html")
        } else {
            exclude("index.html", "webgpu.html")
        }
        into(pagesPath)
    }
    if (indexFile != "index.html") {
        from(project(projectPath).layout.buildDirectory.file("$webappPath/$indexFile")) {
            rename { "index.html" }
            into(pagesPath)
        }
    }
}

fun writeSelectorPage(output: File, title: String, links: List<Pair<String, String>>) {
    output.parentFile.mkdirs()
    val linkHtml = links.joinToString("\n") { (label, href) ->
        """        <a href="$href">$label</a>"""
    }
    output.writeText(
        """
        <!doctype html>
        <html lang="en">
          <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>libfdx $title</title>
            <style>
              :root { color-scheme: light dark; font-family: Arial, Helvetica, sans-serif; }
              body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #f6f7f9; color: #1f2328; }
              main { width: min(720px, calc(100% - 48px)); }
              h1 { margin: 0 0 12px; font-size: 3rem; line-height: 1; }
              p { margin: 0 0 24px; font-size: 1.125rem; line-height: 1.6; }
              nav { display: flex; flex-wrap: wrap; gap: 12px; }
              a { color: #0969da; font-weight: 700; text-decoration: none; }
              a:hover, a:focus { text-decoration: underline; }
              @media (max-width: 560px) {
                h1 { font-size: 2.25rem; }
              }
              @media (prefers-color-scheme: dark) {
                body { background: #0d1117; color: #f0f6fc; }
                a { color: #58a6ff; }
              }
            </style>
          </head>
          <body>
            <main>
              <h1>$title</h1>
              <p>Select a web runtime and graphics API.</p>
              <nav aria-label="$title modes">
        $linkHtml
              </nav>
            </main>
          </body>
        </html>
        """.trimIndent(),
        Charsets.UTF_8
    )
}

fun isMavenPublishingTaskRequested(): Boolean {
    val explicitPublishingTasks = setOf(
        "cleanReleaseStagingDirectory",
        "cleanSnapshotDeployDirectory",
        "listMavenDeployProjects",
        "prepareGradlePluginReleaseDeploy",
        "prepareGradlePluginSnapshotDeploy",
        "prepareReleaseDeploy",
        "prepareSnapshotDeploy",
        "publishRelease",
        "publishSnapshot",
        "uploadSnapshotDeploy",
        "uploadToMavenCentral",
        "validateRuntimeFdxNativeResources",
        "zipStagingDeploy"
    )
    return gradle.startParameter.taskNames
        .map { it.substringAfterLast(":") }
        .any { taskName ->
            taskName in explicitPublishingTasks || taskName.startsWith("publish")
        }
}

if (isMavenPublishingTaskRequested()) {
    extra["libfdxPublishTarget"] = LibfdxPublishTarget.LIBRARIES
    apply(plugin = "publish")
}
