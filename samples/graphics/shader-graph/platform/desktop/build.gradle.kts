plugins {
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("sample_shader_graph_desktop")
}

val sampleProjectPath = project.path.substringBefore(":platform:")
val sampleRoot = layout.projectDirectory.dir("../..")
val editorRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
}

dependencies {
    implementation(project("$sampleProjectPath:core"))
    compileOnly(project(":libfdx:tools:shader-graph-editor"))
    editorRuntime(project(":libfdx:tools:shader-graph-editor"))
    implementation(project(":libfdx:backends:desktop"))
    implementation(project(":libfdx:extensions:graphics:d3d12:core"))
    implementation(project(":libfdx:extensions:graphics:wgpu:core"))
    runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop"))
    runtimeOnly(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
    runtimeOnly(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
}

libfdx {
    assets(sampleRoot.dir("assets"))

    desktopJvm {
        mainClass.set("io.github.libfdx.samples.shadergraph.desktop.ShaderGraphSampleDesktopLauncher")
        workingDir.set(sampleRoot)
        forwardSystemProperty("libfdx.sample.capture")
        forwardSystemProperty("libfdx.sample.captureFrame")
        forwardSystemProperty("libfdx.sample.exitAfterFrames")
        forwardSystemProperty("libfdx.sample.graphSource")
        forwardSystemProperty("libfdx.sample.maximized")
        forwardSystemProperty("libfdx.sample.visible")
        forwardSystemProperty("libfdx.sample.vsync")

        val providers = mapOf("gl" to "GL", "wgpu" to "WGPU", "vulkan" to "Vulkan", "d3d12" to "Direct3D 12")
        for ((provider, label) in providers) {
            val examples = mapOf("" to "DISSOLVE", "_basalt" to "BASALT", "_vortex" to "VORTEX")
            fun registerExample(name: String, example: String, standalone: Boolean, code: Boolean) {
                target(name) {
                    val app = if (standalone) "Standalone" else "Editor"
                    val source = if (code) "Java" else "file"
                    displayName.set("Shader Graph $app · $example from $source ($label)")
                    runDescription.set("Launches the shader graph $app with $example from $source on $label.")
                    if (!standalone) runtimeClasspath(editorRuntime)
                    systemProperty("libfdx.sample.graphics", provider)
                    systemProperty("libfdx.sample.graphicsLabel", label)
                    launchProperty("graphics", provider)
                    launchProperty("graphicsLabel", label)
                    systemProperty("libfdx.sample.example", example)
                    launchProperty("example", example)
                    systemProperty("libfdx.sample.standalone", standalone.toString())
                    launchProperty("standalone", standalone.toString())
                    if (code) {
                        systemProperty("libfdx.sample.graphSource", "code")
                        launchProperty("graphSource", "code")
                    }
                }
            }
            for ((suffix, example) in examples) {
                for (code in listOf(false, true)) {
                    val source = if (code) "_code" else ""
                    registerExample("$provider$suffix$source", example, false, code)
                    registerExample("${provider}_standalone$suffix$source", example, true, code)
                }
            }
            registerExample("${provider}_editor", "DISSOLVE", false, false)
        }
    }
}

tasks.register<JavaExec>("shader_graph_sample_verify_reload") {
    group = "verification"
    description = "Checks cache reuse and last-good editor compilation, then opens the graph on WGPU."
    dependsOn("$sampleProjectPath:core:test", ":libfdx:extensions:graphics:shader-graph:ui-kit:test")
    classpath(tasks.named("libfdx_desktop_jvm_wgpu_build"))
    mainClass.set("io.github.libfdx.samples.shadergraph.desktop.ShaderGraphSampleDesktopLauncher")
    workingDir = sampleRoot.asFile
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("libfdx.sample.exitAfterFrames", "4")
}
