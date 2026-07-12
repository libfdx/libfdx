import io.github.libfdx.build.LibExt
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension

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

subprojects {
    fun configureJava25() {
        extensions.configure(JavaPluginExtension::class.java) {
            sourceCompatibility = JavaVersion.toVersion(25)
            targetCompatibility = JavaVersion.toVersion(25)
        }
    }

    fun implementationProject(projectPath: String) {
        dependencies.add("implementation", rootProject.project(projectPath))
    }

    fun runtimeOnlyProject(projectPath: String) {
        dependencies.add("runtimeOnly", rootProject.project(projectPath))
    }

    fun archiveName(name: String) {
        extensions.configure(BasePluginExtension::class.java) {
            archivesName.set(name)
        }
    }

    fun ecsPlatformerGroup() {
        group = "${LibExt.fdxGroup}.samples.ecs.platformer"
    }

    when (path) {
        ":samples:ecs-platformer:platform:desktop" -> pluginManager.withPlugin("io.github.libfdx") {
            ecsPlatformerGroup()
            configureJava25()
            archiveName("sample_ecs_platformer_desktop")
            implementationProject(":samples:ecs-platformer:core")
            implementationProject(":libfdx:framework:application")
            implementationProject(":libfdx:framework:display")
            implementationProject(":libfdx:extensions:graphics:wgpu:core")
            implementationProject(":libfdx:backends:desktop")
            runtimeOnlyProject(":libfdx:extensions:graphics:gl:platform:desktop")
            runtimeOnlyProject(":libfdx:extensions:graphics:vulkan:platform:desktop")
            runtimeOnlyProject(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm")
        }

        ":samples:ecs-platformer:platform:web" -> pluginManager.withPlugin("io.github.libfdx") {
            ecsPlatformerGroup()
            configureJava25()
            archiveName("sample_ecs_platformer_web")
            implementationProject(":samples:ecs-platformer:core")
            dependencies.add("implementation", libs.teavm.jso)
            dependencies.add("implementation", libs.teavm.jso.apis)
            dependencies.add("implementation", libs.teavm.jso.impl)
            implementationProject(":libfdx:backends:web")
            implementationProject(":libfdx:extensions:graphics:gl:platform:web")
            implementationProject(":libfdx:extensions:graphics:wgpu:platform:web")
        }

        ":samples:ecs-platformer:platform:desktop_c" -> pluginManager.withPlugin("io.github.libfdx") {
            ecsPlatformerGroup()
            configureJava25()
            archiveName("sample_ecs_platformer_desktop_c")
            implementationProject(":samples:ecs-platformer:core")
            implementationProject(":libfdx:backends:desktop_c")
            runtimeOnlyProject(":libfdx:extensions:graphics:gl:platform:desktop_c")
        }

        ":samples:ecs-platformer:platform:ios_c" -> pluginManager.withPlugin("io.github.libfdx") {
            ecsPlatformerGroup()
            configureJava25()
            archiveName("sample_ecs_platformer_ios_c")
            implementationProject(":samples:ecs-platformer:core")
            implementationProject(":libfdx:backends:ios_c")
        }

        ":samples:ecs-platformer:platform:android" -> pluginManager.withPlugin("com.android.application") {
            ecsPlatformerGroup()
            archiveName("sample_ecs_platformer_android")
            implementationProject(":samples:ecs-platformer:core")
            implementationProject(":libfdx:backends:android")
            implementationProject(":libfdx:extensions:graphics:wgpu:platform:android_jni")
            implementationProject(":libfdx:extensions:graphics:vulkan:platform:android_jni")
        }
    }
}

fun runtimeFdxHostNativeTaskPath(): String {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("windows") -> ":libfdx:framework:fdx:platform:desktop:generate_runtime_fdx_windows_native"
        os.contains("linux") -> ":libfdx:framework:fdx:platform:desktop:generate_runtime_fdx_linux_native"
        os.contains("mac") || os.contains("darwin") -> ":libfdx:framework:fdx:platform:desktop:generate_runtime_fdx_macos_native"
        else -> throw GradleException("Unsupported host OS for runtime fdx native artifacts: ${System.getProperty("os.name")}")
    }
}

val nativeArtifactTaskPaths = listOf(
    runtimeFdxHostNativeTaskPath(),
    ":libfdx:framework:fdx:platform:web:generate_runtime_fdx_web_native",
    ":libfdx:framework:fdx:platform:android:assembleRelease",
    ":libfdx:backends:android:assembleRelease",
    ":libfdx:extensions:graphics:vulkan:platform:android_jni:assembleRelease",
    ":libfdx:extensions:graphics:wgpu:platform:android_jni:assembleRelease",
    ":libfdx:extensions:net:webrtc:platform:android_jni:assembleRelease"
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
    dependsOn(":libfdx:framework:fdx:platform:desktop:generate_runtime_fdx_windows_native_prebuilt")
}

tasks.register("libfdx_build_linux_native_artifact_prebuilt") {
    group = "libfdx native"
    description = "Builds the Linux runtime fdx native artifact using prebuilt fdx-natives dependency packages."
    dependsOn(":libfdx:framework:fdx:platform:desktop:generate_runtime_fdx_linux_native_prebuilt")
}

tasks.register("libfdx_build_macos_native_artifact_prebuilt") {
    group = "libfdx native"
    description = "Builds the macOS runtime fdx native artifact for the current runner using prebuilt fdx-natives dependency packages."
    dependsOn(":libfdx:framework:fdx:platform:desktop:generate_runtime_fdx_macos_native_prebuilt")
}

tasks.register("libfdx_build_web_native_artifacts_prebuilt") {
    group = "libfdx native"
    description = "Builds web runtime fdx native artifacts using prebuilt fdx-natives dependency packages."
    dependsOn(":libfdx:framework:fdx:platform:web:generate_runtime_fdx_web_native_prebuilt")
}

tasks.register("libfdx_build_android_native_artifacts_prebuilt") {
    group = "libfdx native"
    description = "Builds Android native/library artifacts using prebuilt fdx-natives dependency packages for runtime fdx."
    dependsOn(
        ":libfdx:framework:fdx:platform:android:assemble_runtime_fdx_android_release_prebuilt",
        ":libfdx:backends:android:assembleRelease",
        ":libfdx:extensions:graphics:vulkan:platform:android_jni:assembleRelease",
        ":libfdx:extensions:graphics:wgpu:platform:android_jni:assembleRelease",
        ":libfdx:extensions:net:webrtc:platform:android_jni:assembleRelease"
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
        pagesPath = "project-generator/webgpu-js"
    )
    pagesWebapp(
        projectPath = ":tests:platform:web",
        buildTaskName = "test_webgl_js_build",
        webappPath = "dist/web-js/webapp",
        pagesPath = "tests/webgl-js",
        outputProjectPath = ":tests:platform:plugin"
    )
    pagesWebapp(
        projectPath = ":tests:platform:web",
        buildTaskName = "test_webgl_wasm_build",
        webappPath = "dist/web-wasm/webapp",
        pagesPath = "tests/webgl-wasm",
        outputProjectPath = ":tests:platform:plugin"
    )
    pagesWebapp(
        projectPath = ":tests:platform:web",
        buildTaskName = "test_webgpu_js_build",
        webappPath = "dist/web-js/webapp",
        pagesPath = "tests/webgpu-js",
        outputProjectPath = ":tests:platform:plugin"
    )
    pagesWebapp(
        projectPath = ":samples:basic:platform:web",
        buildTaskName = "basic_webgl_js_build",
        webappPath = "dist/web-js/webapp",
        pagesPath = "samples/basic/webgl-js",
        outputProjectPath = ":samples:basic:platform:plugin"
    )
    pagesWebapp(
        projectPath = ":samples:basic:platform:web",
        buildTaskName = "basic_webgl_wasm_build",
        webappPath = "dist/web-wasm/webapp",
        pagesPath = "samples/basic/webgl-wasm",
        outputProjectPath = ":samples:basic:platform:plugin"
    )
    pagesWebapp(
        projectPath = ":samples:basic:platform:web",
        buildTaskName = "basic_webgpu_js_build",
        webappPath = "dist/web-js/webapp",
        pagesPath = "samples/basic/webgpu-js",
        outputProjectPath = ":samples:basic:platform:plugin"
    )
    pagesWebapp(
        projectPath = ":samples:ecs-platformer:platform:web",
        buildTaskName = "libfdx_web_js_webgl_build",
        webappPath = "dist/web-js/webapp",
        pagesPath = "samples/ecs-platformer/webgl-js"
    )
    pagesWebapp(
        projectPath = ":samples:ecs-platformer:platform:web",
        buildTaskName = "libfdx_web_wasm_webgl_build",
        webappPath = "dist/web-wasm/webapp",
        pagesPath = "samples/ecs-platformer/webgl-wasm"
    )
    pagesWebapp(
        projectPath = ":samples:ecs-platformer:platform:web",
        buildTaskName = "libfdx_web_js_webgpu_build",
        webappPath = "dist/web-js/webapp",
        pagesPath = "samples/ecs-platformer/webgpu-js"
    )
    doLast {
        val root = pagesStagingDir.get().asFile
        writeSelectorPage(
            root.resolve("project-generator/index.html"),
            "Project Generator",
            listOf(
                "WebGL JS" to "webgl-js/",
                "WebGL Wasm" to "webgl-wasm/",
                "WebGPU JS" to "webgpu-js/?graphics=webgpu"
            )
        )
        writeSelectorPage(
            root.resolve("tests/index.html"),
            "Tests",
            listOf(
                "WebGL JS" to "webgl-js/",
                "WebGL Wasm" to "webgl-wasm/",
                "WebGPU JS" to "webgpu-js/?graphics=webgpu"
            )
        )
        writeSelectorPage(
            root.resolve("samples/basic/index.html"),
            "Basic Sample",
            listOf(
                "WebGL JS" to "webgl-js/",
                "WebGL Wasm" to "webgl-wasm/",
                "WebGPU JS" to "webgpu-js/?graphics=webgpu"
            )
        )
        writeSelectorPage(
            root.resolve("samples/ecs-platformer/index.html"),
            "ECS Platformer",
            listOf(
                "WebGL JS" to "webgl-js/",
                "WebGL Wasm" to "webgl-wasm/",
                "WebGPU JS" to "webgpu-js/?graphics=webgpu"
            )
        )
    }
}

fun Sync.pagesWebapp(
    projectPath: String,
    buildTaskName: String,
    webappPath: String,
    pagesPath: String,
    outputProjectPath: String = projectPath
) {
    dependsOn("$projectPath:$buildTaskName")
    from(project(outputProjectPath).layout.buildDirectory.dir(webappPath)) {
        exclude("webgpu.html")
        into(pagesPath)
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

apply(plugin = "publish")
