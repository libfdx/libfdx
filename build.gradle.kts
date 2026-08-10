import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
    id("base")
    alias(libs.plugins.easyPublishing)
}

System.getProperty("libfdx.compositeBuildDir")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.let { isolatedRootPath ->
        val isolatedRoot = file(isolatedRootPath)
        allprojects {
            if(path != ":libfdx:framework:fdx:fdx-build") {
                val relativeProjectPath = if(path == ":") "root"
                else path.removePrefix(":").replace(':', '/')
                layout.buildDirectory.set(isolatedRoot.resolve(relativeProjectPath))
            }
        }
    }

val libfdxGroup = libs.versions.libfdxGroup.get()
val libfdxVersion = libs.versions.libfdxRelease.get()
val libfdxSnapshotVersion = libs.versions.libfdxSnapshot.get()
gradle.extensions.extraProperties.set("libfdxDependencyVersion", libfdxSnapshotVersion)
val useLocalJBox3DSnapshot = providers.gradleProperty("jbox3d.local")
    .orElse(providers.gradleProperty("libfdx.local"))
    .map(String::toBoolean)
    .orElse(false)

val externalExtensionVersions = linkedMapOf(
    "jBox2D" to libs.versions.jbox2d.get(),
    "jBox3D" to libs.versions.jbox3d.get(),
    "jJolt" to libs.versions.jjolt.get(),
    "jImGui" to libs.versions.jimgui.get(),
)

val verifyExternalExtensionReleaseVersions =
    tasks.register("verify_external_extension_release_versions") {
        group = "verification"
        description = "Rejects libFDX releases that depend on snapshot external extension APIs."
        doLast {
            val snapshots = externalExtensionVersions.filterValues { version ->
                version.contains("SNAPSHOT", ignoreCase = true)
            }
            if (snapshots.isNotEmpty()) {
                val dependencies = snapshots.entries.joinToString { (name, version) ->
                    "$name=$version"
                }
                throw GradleException(
                    "Cannot release libFDX while external extension APIs use snapshots: $dependencies"
                )
            }
        }
    }

val libfdxPublishableProjectPaths = listOf(
    ":libfdx:framework:math",
    ":libfdx:framework:json",
    ":libfdx:framework:collections",
    ":libfdx:framework:fdx:core",
    ":libfdx:framework:fdx:platform:shared",
    ":libfdx:framework:fdx:platform:desktop",
    ":libfdx:framework:fdx:platform:android",
    ":libfdx:framework:fdx:platform:web",
    ":libfdx:framework:application",
    ":libfdx:framework:display",
    ":libfdx:framework:files",
    ":libfdx:framework:input",
    ":libfdx:framework:net",
    ":libfdx:framework:storage",
    ":libfdx:framework:assets:manager",
    ":libfdx:framework:assets:loaders",
    ":libfdx:framework:graphics",
    ":libfdx:framework:camera",
    ":libfdx:framework:g2d",
    ":libfdx:framework:g3d",
    ":libfdx:framework:ui-kit",
    ":libfdx:extensions:scenario_validator:core",
    ":libfdx:extensions:scenario_validator:ui-kit",
    ":libfdx:extensions:physics:box2d:core",
    ":libfdx:extensions:physics:box3d:core",
    ":libfdx:extensions:physics:jolt:core",
    ":libfdx:extensions:ui:imgui:core",
    ":libfdx:tools:font",
    ":libfdx:tools:shader",
    ":libfdx:extensions:graphics:gl:core",
    ":libfdx:extensions:graphics:gl:platform:desktop",
    ":libfdx:extensions:graphics:gl:platform:desktop_c",
    ":libfdx:extensions:graphics:gl:platform:web",
    ":libfdx:extensions:graphics:vulkan:core",
    ":libfdx:extensions:graphics:vulkan:platform:desktop",
    ":libfdx:extensions:graphics:vulkan:platform:desktop_c",
    ":libfdx:extensions:graphics:vulkan:platform:android_jni",
    ":libfdx:extensions:graphics:d3d12:core",
    ":libfdx:extensions:graphics:wgpu:core",
    ":libfdx:extensions:graphics:wgpu:platform:desktop_jni",
    ":libfdx:extensions:graphics:wgpu:platform:desktop_ffm",
    ":libfdx:extensions:graphics:wgpu:platform:android_jni",
    ":libfdx:extensions:graphics:wgpu:platform:web",
    ":libfdx:extensions:graphics:shader-graph:core",
    ":libfdx:extensions:graphics:shader-graph:runtime",
    ":libfdx:extensions:graphics:shader-graph:g2d",
    ":libfdx:extensions:graphics:shader-graph:g3d",
    ":libfdx:extensions:graphics:shader-graph:ui-kit",
    ":libfdx:extensions:net:webrtc:core",
    ":libfdx:extensions:net:webrtc:signaling_server",
    ":libfdx:extensions:net:webrtc:platform:desktop_jni",
    ":libfdx:extensions:net:webrtc:platform:web",
    ":libfdx:extensions:net:webrtc:platform:android_jni",
    ":libfdx:backends:desktop",
    ":libfdx:backends:desktop_c",
    ":libfdx:backends:ios_c",
    ":libfdx:backends:psp",
    ":libfdx:backends:android",
    ":libfdx:backends:web",
    ":libfdx:backends:c_shared"
)

easyPublishing {
    modules(libfdxPublishableProjectPaths)
    groupId.set(libfdxGroup)
    releaseVersion.set(libfdxVersion)
    snapshotVersion.set(libfdxSnapshotVersion)

    snapshotRepositoryUrl.set("https://central.sonatype.com/repository/maven-snapshots/")
    releaseRepositoryUrl.set("https://central.sonatype.com")
    username.set(providers.environmentVariable("CENTRAL_PORTAL_USERNAME"))
    password.set(providers.environmentVariable("CENTRAL_PORTAL_PASSWORD"))
    signingKey.set(providers.environmentVariable("SIGNING_KEY"))
    signingPassword.set(providers.environmentVariable("SIGNING_PASSWORD"))
    automaticRelease.set(
        providers.environmentVariable("CENTRAL_PUBLISHING_TYPE")
            .map { it.equals("AUTOMATIC", ignoreCase = true) }
            .orElse(false)
    )

    pomName.set("libFDX")
    pomDescription.set("A modular, cross-platform Java framework for games and interactive applications.")
    projectUrl.set("https://github.com/libfdx/libfdx")
    developerId.set("Xpe")
    developerName.set("Natan")
    scmUrl.set("https://github.com/libfdx/libfdx")
    scmConnection.set("scm:git:https://github.com/libfdx/libfdx.git")
    scmDeveloperConnection.set("scm:git:ssh://git@github.com/libfdx/libfdx.git")

    nestedBuild("gradle-plugin") {
        directory.set(layout.projectDirectory.dir("libfdx/tools/gradle-plugin"))
    }
}

tasks.matching { task ->
    task.name == "prepareRelease" || task.name == "publishRelease"
}.configureEach {
    dependsOn(verifyExternalExtensionReleaseVersions)
}

allprojects {
    version = libfdxVersion

    repositories {
        if(useLocalJBox3DSnapshot.get()) {
            mavenLocal()
        }
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
    description = "Prints the libFDX version from the version catalog."
    doLast {
        println(libfdxVersion)
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
        buildTaskName = "project_generator_webgpu_wasm_build",
        webappPath = "dist/webgpu-wasm/webapp",
        pagesPath = "project-generator"
    )
    pagesWebapp(
        projectPath = ":tests:platform:web",
        buildTaskName = "libfdx_web_js_webgl_build",
        webappPath = "dist/web-js/webapp",
        pagesPath = "tests/webgl-js"
    )
    pagesWebapp(
        projectPath = ":tests:platform:web",
        buildTaskName = "libfdx_web_wasm_webgl_build",
        webappPath = "dist/web-wasm/webapp",
        pagesPath = "tests/webgl-wasm"
    )
    pagesWebapp(
        projectPath = ":tests:platform:web",
        buildTaskName = "libfdx_web_js_webgpu_build",
        webappPath = "dist/web-js/webapp",
        pagesPath = "tests/webgpu-js"
    )
    pagesWebapp(
        projectPath = ":samples:2d:sprite-movement:platform:web",
        buildTaskName = "libfdx_web_js_webgl_build",
        webappPath = "dist/web-js/webapp",
        pagesPath = "samples/2d/sprite-movement/webgl-js"
    )
    pagesWebapp(
        projectPath = ":samples:2d:sprite-movement:platform:web",
        buildTaskName = "libfdx_web_wasm_webgl_build",
        webappPath = "dist/web-wasm/webapp",
        pagesPath = "samples/2d/sprite-movement/webgl-wasm"
    )
    pagesWebapp(
        projectPath = ":samples:2d:sprite-movement:platform:web",
        buildTaskName = "libfdx_web_js_webgpu_build",
        webappPath = "dist/web-js/webapp",
        pagesPath = "samples/2d/sprite-movement/webgpu-js"
    )
    pagesWebapp(
        projectPath = ":samples:2d:platformer:platform:web",
        buildTaskName = "libfdx_web_js_webgl_build",
        webappPath = "dist/web-js/webapp",
        pagesPath = "samples/2d/platformer/webgl-js"
    )
    pagesWebapp(
        projectPath = ":samples:2d:platformer:platform:web",
        buildTaskName = "libfdx_web_wasm_webgl_build",
        webappPath = "dist/web-wasm/webapp",
        pagesPath = "samples/2d/platformer/webgl-wasm"
    )
    pagesWebapp(
        projectPath = ":samples:2d:platformer:platform:web",
        buildTaskName = "libfdx_web_js_webgpu_build",
        webappPath = "dist/web-js/webapp",
        pagesPath = "samples/2d/platformer/webgpu-js"
    )
    doLast {
        val root = pagesStagingDir.get().asFile
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
            root.resolve("samples/2d/index.html"),
            "2D Samples",
            listOf(
                "Sprite Movement" to "sprite-movement/",
                "Platformer" to "platformer/"
            )
        )
        writeSelectorPage(
            root.resolve("samples/2d/sprite-movement/index.html"),
            "2D Sprite Movement",
            listOf(
                "WebGL JS" to "webgl-js/",
                "WebGL Wasm" to "webgl-wasm/",
                "WebGPU JS" to "webgpu-js/?graphics=webgpu"
            )
        )
        writeSelectorPage(
            root.resolve("samples/2d/platformer/index.html"),
            "Platformer",
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

fun writeRedirectPage(output: File, target: String) {
    output.parentFile.mkdirs()
    val scriptTarget = if ('?' in target) {
        """"$target" + location.hash"""
    } else {
        """"$target" + location.search + location.hash"""
    }
    output.writeText(
        """
        <!doctype html>
        <html lang="en">
          <head>
            <meta charset="utf-8">
            <meta http-equiv="refresh" content="0; url=$target">
            <title>Platformer moved</title>
            <script>location.replace($scriptTarget);</script>
          </head>
          <body>
            <p>This sample moved to <a href="$target">Platformer</a>.</p>
          </body>
        </html>
        """.trimIndent(),
        Charsets.UTF_8
    )
}

gradle.projectsEvaluated {
    val libraryPublishingTasks = libfdxPublishableProjectPaths.flatMap { projectPath ->
        project(projectPath).tasks.withType(PublishToMavenRepository::class.java)
            .matching { it.name.endsWith("ToEasyPublishingRepository") }
            .toList()
    }

    listOf(
        "prepareGradlePluginSnapshot",
        "publishGradlePluginSnapshot",
        "prepareGradlePluginRelease"
    ).forEach { taskName ->
        tasks.named(taskName) {
            dependsOn(libraryPublishingTasks)
        }
    }
}
