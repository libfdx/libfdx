
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.tasks.Delete
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}


val glRuntimeClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val vulkanRuntimeClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val wgpuRuntimeClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

val runtimeFdxClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

base {
    archivesName.set("tests_desktop")
}

dependencies {
    implementation(project(":tests:core"))
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_desktop:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:d3d12_core:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:wgpu_core:${libs.versions.libfdxSnapshot.get()}")

        glRuntimeClasspath("${libs.versions.libfdxGroup.get()}:gl_desktop:${libs.versions.libfdxSnapshot.get()}")
        vulkanRuntimeClasspath("${libs.versions.libfdxGroup.get()}:vulkan_desktop:${libs.versions.libfdxSnapshot.get()}")
        wgpuRuntimeClasspath("${libs.versions.libfdxGroup.get()}:wgpu_desktop_ffm:${libs.versions.libfdxSnapshot.get()}")
        runtimeFdxClasspath("${libs.versions.libfdxGroup.get()}:fdx_desktop:${libs.versions.libfdxSnapshot.get()}")
    } else {
        implementation(project(":libfdx:backends:desktop"))
        implementation(project(":libfdx:extensions:graphics:d3d12:core"))
        implementation(project(":libfdx:extensions:graphics:wgpu:core"))

        glRuntimeClasspath(project(":libfdx:extensions:graphics:gl:platform:desktop"))
        vulkanRuntimeClasspath(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
        wgpuRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
        runtimeFdxClasspath(project(":libfdx:framework:fdx:platform:desktop"))
    }
}

fun registerGraphicsTestRun(
    taskName: String,
    descriptionText: String,
    graphics: String,
    graphicsLabel: String,
    providerClasspath: FileCollection
) = tasks.register<JavaExec>(taskName) {
    group = "application"
    description = descriptionText
    classpath = sourceSets["main"].runtimeClasspath + providerClasspath
    mainClass.set("io.github.libfdx.tests.desktop.DesktopTestLauncher")
    systemProperty("libfdx.test.graphics", graphics)
    systemProperty("libfdx.test.graphicsLabel", graphicsLabel)
}

val d3d12Run = registerGraphicsTestRun(
    "test_desktop_d3d12_run",
    "Runs graphics tests with Direct3D 12 through Java 25 FFM on Windows.",
    "d3d12",
    "Direct3D 12",
    files()
)
val glRun = registerGraphicsTestRun(
    "test_desktop_gl_run",
    "Runs graphics tests with desktop GL.",
    "gl",
    "GL",
    glRuntimeClasspath
)
val wgpuRun = registerGraphicsTestRun(
    "test_desktop_wgpu_run",
    "Runs graphics tests with WGPU.",
    "wgpu",
    "WGPU",
    wgpuRuntimeClasspath
)
val wgpuCompute = registerGraphicsTestRun(
    "test_desktop_wgpu_compute",
    "Dispatches and verifies a handwritten compute shader through the common graphics API.",
    "wgpu",
    "WGPU",
    wgpuRuntimeClasspath
).apply {
    configure {
        group = "verification"
        doFirst {
            systemProperty("libfdx.test.name", "compute-buffer")
            systemProperty("libfdx.test.frames", "4")
            systemProperty("libfdx.test.visible", "false")
            systemProperty("libfdx.test.width", "320")
            systemProperty("libfdx.test.height", "240")
            systemProperty("libfdx.test.maximized", "false")
            systemProperty("libfdx.test.vsync", "false")
        }
    }
}
val wgpuRenderTargets = registerGraphicsTestRun(
    "test_desktop_wgpu_render_targets",
    "Validates MRT, resolves, explicit depth, multisampling, and pipeline compatibility through WGPU.",
    "wgpu",
    "WGPU",
    wgpuRuntimeClasspath
).apply {
    configure {
        group = "verification"
        doFirst {
            systemProperty("libfdx.test.name", "render-target-compatibility")
            systemProperty("libfdx.test.frames", "3")
            systemProperty("libfdx.test.visible", "false")
            systemProperty("libfdx.test.width", "320")
            systemProperty("libfdx.test.height", "240")
            systemProperty("libfdx.test.maximized", "false")
            systemProperty("libfdx.test.vsync", "false")
        }
    }
}
val wgpuShaderGraphProgram = registerGraphicsTestRun(
    "test_desktop_wgpu_shader_graph_program",
    "Compiles and renders a complete graph-owned vertex/fragment MRT program through ShaderProvider and WGPU.",
    "wgpu",
    "WGPU",
    wgpuRuntimeClasspath
).apply {
    configure {
        group = "verification"
        doFirst {
            systemProperty("libfdx.test.name", "shader-graph-program")
            systemProperty("libfdx.test.frames", "3")
            systemProperty("libfdx.test.visible", "false")
            systemProperty("libfdx.test.width", "320")
            systemProperty("libfdx.test.height", "240")
            systemProperty("libfdx.test.maximized", "false")
            systemProperty("libfdx.test.vsync", "false")
        }
    }
}
val wgpuShaderGraphTechnique = registerGraphicsTestRun(
    "test_desktop_wgpu_shader_graph_technique",
    "Runs a multi-pass graph technique with variants, fallback, bounded caches, and atomic replacement through WGPU.",
    "wgpu",
    "WGPU",
    wgpuRuntimeClasspath
).apply {
    configure {
        group = "verification"
        doFirst {
            systemProperty("libfdx.test.name", "shader-graph-technique")
            systemProperty("libfdx.test.frames", "3")
            systemProperty("libfdx.test.visible", "false")
            systemProperty("libfdx.test.width", "320")
            systemProperty("libfdx.test.height", "240")
            systemProperty("libfdx.test.maximized", "false")
            systemProperty("libfdx.test.vsync", "false")
        }
    }
}
val wgpuShaderGraphSprite = registerGraphicsTestRun(
    "test_desktop_wgpu_shader_graph_sprite",
    "Renders SpriteBatch through the common ShaderGraphProvider and every negotiated WGPU sprite ABI.",
    "wgpu",
    "WGPU",
    wgpuRuntimeClasspath
).apply {
    configure {
        group = "verification"
        doFirst {
            systemProperty("libfdx.test.name", "shader-graph-sprite")
            systemProperty("libfdx.test.frames", "4")
            systemProperty("libfdx.test.visible", "false")
            systemProperty("libfdx.test.width", "640")
            systemProperty("libfdx.test.height", "480")
            systemProperty("libfdx.test.maximized", "false")
            systemProperty("libfdx.test.vsync", "false")
            systemProperty(
                "libfdx.test.capture",
                layout.buildDirectory.file(
                    "captures/shader-graph-sprite.ppm"
                ).get().asFile.absolutePath
            )
            systemProperty("libfdx.test.captureFrame", "2")
        }
    }
}
val wgpuShaderGraphModel = registerGraphicsTestRun(
    "test_desktop_wgpu_shader_graph_model",
    "Renders static ModelBatch PBR through the common ShaderGraphProvider.",
    "wgpu",
    "WGPU",
    wgpuRuntimeClasspath
).apply {
    configure {
        group = "verification"
        doFirst {
            systemProperty("libfdx.test.name", "model")
            systemProperty("libfdx.test.shaderGraphPbr", "true")
            systemProperty("libfdx.test.frames", "32")
            systemProperty("libfdx.test.visible", "false")
            systemProperty("libfdx.test.width", "640")
            systemProperty("libfdx.test.height", "480")
            systemProperty("libfdx.test.maximized", "false")
            systemProperty("libfdx.test.vsync", "false")
            systemProperty(
                "libfdx.test.capture",
                layout.buildDirectory.file(
                    "captures/shader-graph-model.ppm"
                ).get().asFile.absolutePath
            )
        }
    }
}
val wgpuShaderGraphSkinnedModel = registerGraphicsTestRun(
    "test_desktop_wgpu_shader_graph_skinned_model",
    "Renders skinned ModelBatch PBR through the common ShaderGraphProvider.",
    "wgpu",
    "WGPU",
    wgpuRuntimeClasspath
).apply {
    configure {
        group = "verification"
        doFirst {
            systemProperty("libfdx.test.name", "model-skinning")
            systemProperty("libfdx.test.shaderGraphPbr", "true")
            systemProperty("libfdx.test.frames", "46")
            systemProperty("libfdx.test.captureFrame", "44")
            systemProperty("libfdx.test.visible", "false")
            systemProperty("libfdx.test.width", "640")
            systemProperty("libfdx.test.height", "480")
            systemProperty("libfdx.test.maximized", "false")
            systemProperty("libfdx.test.vsync", "false")
            systemProperty(
                "libfdx.test.capture",
                layout.buildDirectory.file(
                    "captures/shader-graph-model-skinned.ppm"
                ).get().asFile.absolutePath
            )
        }
    }
}
val wgpuShaderGraphCompute = registerGraphicsTestRun(
    "test_desktop_wgpu_shader_graph_compute",
    "Dispatches graph-generated buffer, workgroup, atomic, storage-texture, and compute-to-render programs through WGPU.",
    "wgpu",
    "WGPU",
    wgpuRuntimeClasspath
).apply {
    configure {
        group = "verification"
        doFirst {
            systemProperty("libfdx.test.name", "shader-graph-compute")
            systemProperty("libfdx.test.frames", "4")
            systemProperty("libfdx.test.visible", "false")
            systemProperty("libfdx.test.width", "320")
            systemProperty("libfdx.test.height", "240")
            systemProperty("libfdx.test.maximized", "false")
            systemProperty("libfdx.test.vsync", "false")
            systemProperty(
                "libfdx.test.capture",
                layout.buildDirectory.file(
                    "captures/shader-graph-compute.ppm"
                ).get().asFile.absolutePath
            )
            systemProperty("libfdx.test.captureFrame", "2")
        }
    }
}
val wgpuUiCustomSurface = registerGraphicsTestRun(
    "test_desktop_wgpu_ui_custom_surface",
    "Renders and captures UI Kit custom surfaces, clipping, lines, and retained paths through WGPU.",
    "wgpu",
    "WGPU",
    wgpuRuntimeClasspath
).apply {
    configure {
        group = "verification"
        doFirst {
            systemProperty("libfdx.test.name", "ui-custom-surface")
            systemProperty("libfdx.test.frames", "4")
            systemProperty("libfdx.test.visible", "false")
            systemProperty("libfdx.test.width", "960")
            systemProperty("libfdx.test.height", "600")
            systemProperty("libfdx.test.maximized", "false")
            systemProperty("libfdx.test.vsync", "false")
            systemProperty(
                "libfdx.test.capture",
                layout.buildDirectory.file(
                    "captures/ui-custom-surface.ppm"
                ).get().asFile.absolutePath
            )
            systemProperty("libfdx.test.captureFrame", "2")
        }
    }
}
val wgpuShaderGraphEditor = registerGraphicsTestRun(
    "test_desktop_wgpu_shader_graph_editor",
    "Renders and captures the complete optional Shader Graph UI Kit editor through WGPU.",
    "wgpu",
    "WGPU",
    wgpuRuntimeClasspath
).apply {
    configure {
        group = "verification"
        doFirst {
            systemProperty("libfdx.test.name", "shader-graph-editor")
            systemProperty("libfdx.test.frames", "4")
            systemProperty("libfdx.test.visible", "false")
            systemProperty("libfdx.test.width", "1440")
            systemProperty("libfdx.test.height", "900")
            systemProperty("libfdx.test.maximized", "false")
            systemProperty("libfdx.test.vsync", "false")
            systemProperty(
                "libfdx.test.capture",
                layout.buildDirectory.file(
                    "captures/shader-graph-editor.ppm"
                ).get().asFile.absolutePath
            )
            systemProperty("libfdx.test.captureFrame", "2")
        }
    }
}
val vulkanRun = registerGraphicsTestRun(
    "test_desktop_vulkan_run",
    "Runs graphics tests with desktop Vulkan.",
    "vulkan",
    "Vulkan",
    vulkanRuntimeClasspath
)

val d3d12PbrStaticCapture =
    layout.buildDirectory.file("reports/shader-phase0/d3d12-smoke/static.ppm")
val d3d12PbrSkinnedCapture =
    layout.buildDirectory.file("reports/shader-phase0/d3d12-smoke/skinned.ppm")
val isWindowsHost = System.getProperty("os.name").lowercase().contains("windows")

val d3d12PbrStaticCompile = registerGraphicsTestRun(
    "test_desktop_d3d12_pbr_static_compile",
    "Compiles and renders the static PBR shader through native D3D12 FXC.",
    "d3d12",
    "Direct3D 12",
    files()
).apply {
    configure {
        group = "verification"
        onlyIf("Native D3D12 FXC is available only on Windows") { isWindowsHost }
        doFirst {
            systemProperty("libfdx.test.name", "model")
            systemProperty("libfdx.test.frames", "32")
            systemProperty("libfdx.test.capture", d3d12PbrStaticCapture.get().asFile.absolutePath)
            systemProperty("libfdx.test.visible", "false")
            systemProperty("libfdx.test.width", "640")
            systemProperty("libfdx.test.height", "480")
            systemProperty("libfdx.test.maximized", "false")
            systemProperty("libfdx.test.vsync", "false")
        }
    }
}

val d3d12PbrSkinnedCompile = registerGraphicsTestRun(
    "test_desktop_d3d12_pbr_skinned_compile",
    "Compiles and renders the skinned PBR shader through native D3D12 FXC.",
    "d3d12",
    "Direct3D 12",
    files()
).apply {
    configure {
        group = "verification"
        onlyIf("Native D3D12 FXC is available only on Windows") { isWindowsHost }
        mustRunAfter(d3d12PbrStaticCompile)
        doFirst {
            systemProperty("libfdx.test.name", "model-skinning")
            systemProperty("libfdx.test.frames", "46")
            systemProperty("libfdx.test.captureFrame", "44")
            systemProperty("libfdx.test.capture", d3d12PbrSkinnedCapture.get().asFile.absolutePath)
            systemProperty("libfdx.test.visible", "false")
            systemProperty("libfdx.test.width", "640")
            systemProperty("libfdx.test.height", "480")
            systemProperty("libfdx.test.maximized", "false")
            systemProperty("libfdx.test.vsync", "false")
        }
    }
}

tasks.register("test_desktop_d3d12_pbr_compile") {
    group = "verification"
    description = "Verifies static and skinned PBR shaders through native D3D12 FXC vs_5_1/ps_5_1."
    onlyIf("Native D3D12 FXC is available only on Windows") { isWindowsHost }
    dependsOn(d3d12PbrStaticCompile, d3d12PbrSkinnedCompile)
}

tasks.register<JavaExec>("test_math_acceleration_desktop") {
    group = "application_test"
    description = "Validates desktop runtime fdx SIMD math acceleration against scalar math."
    classpath = sourceSets["main"].output + sourceSets["main"].compileClasspath + runtimeFdxClasspath
    mainClass.set("io.github.libfdx.backend.desktop.DesktopMathAccelerationCheck")
    systemProperty("libfdx.math.requireNative", "true")
}

val cleanTestRuntimeStorage = tasks.register<Delete>("clean_test_runtime_storage") {
    group = "verification"
    description = "Removes the default persistent store created by StorageRuntimeTest."
    onlyIf {
        gradle.startParameter.systemPropertiesArgs["libfdx.test.storageName"].isNullOrBlank()
    }
    delete(rootProject.layout.projectDirectory.file("storage/runtime-storage-test.json"))
    doLast {
        val storageDirectory = rootProject.layout.projectDirectory.dir("storage").asFile
        if (storageDirectory.isDirectory && storageDirectory.list().isNullOrEmpty()) {
            storageDirectory.delete()
        }
    }
}

listOf(
    d3d12Run,
    glRun,
    wgpuRun,
    wgpuCompute,
    wgpuRenderTargets,
    wgpuShaderGraphProgram,
    wgpuShaderGraphTechnique,
    wgpuShaderGraphSprite,
    wgpuShaderGraphModel,
    wgpuShaderGraphSkinnedModel,
    wgpuShaderGraphCompute,
    vulkanRun,
    d3d12PbrStaticCompile,
    d3d12PbrSkinnedCompile
).forEach { runTask ->
    runTask.configure { finalizedBy(cleanTestRuntimeStorage) }
}

tasks.withType<JavaExec>().configureEach {
    workingDir = rootProject.projectDir
    minHeapSize = "64m"
    maxHeapSize = "1g"
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    jvmArgs("-Dorg.lwjgl.system.stackSize=1024", "--enable-native-access=ALL-UNNAMED")
    gradle.startParameter.systemPropertiesArgs
            .filterKeys {
                (it.startsWith("libfdx.test.") || it.startsWith("libfdx.validation."))
                        && it != "libfdx.test.graphics"
                        && it != "libfdx.test.graphicsLabel"
            }
            .filterValues { it.isNotBlank() }
            .forEach { (name, value) -> systemProperty(name, value) }
}
