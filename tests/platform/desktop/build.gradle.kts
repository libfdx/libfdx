import io.github.libfdx.gradle.LibfdxDesktopJvmTargetExtension
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.tasks.Delete

plugins {
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

val glRuntimeClasspath = configurations.create("glRuntimeClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val vulkanRuntimeClasspath = configurations.create("vulkanRuntimeClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val wgpuRuntimeClasspath = configurations.create("wgpuRuntimeClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

val runtimeFdxClasspath = configurations.create("runtimeFdxClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

val interactiveGraphicsRuntimeClasspath = files(
    glRuntimeClasspath,
    vulkanRuntimeClasspath,
    wgpuRuntimeClasspath,
)

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

fun LibfdxDesktopJvmTargetExtension.graphics(name: String, label: String) {
    systemProperty("libfdx.test.graphics", name)
    systemProperty("libfdx.test.graphicsLabel", label)
    launchProperty("graphics", name)
    launchProperty("graphicsLabel", label)
}

fun LibfdxDesktopJvmTargetExtension.headless(
    testName: String,
    frames: Int,
    width: Int,
    height: Int,
    capture: String? = null,
    captureFrame: Int? = null
) {
    systemProperty("libfdx.test.name", testName)
    systemProperty("libfdx.test.frames", frames.toString())
    systemProperty("libfdx.test.visible", "false")
    systemProperty("libfdx.test.width", width.toString())
    systemProperty("libfdx.test.height", height.toString())
    systemProperty("libfdx.test.maximized", "false")
    systemProperty("libfdx.test.vsync", "false")
    capture?.let { systemProperty("libfdx.test.capture", it) }
    captureFrame?.let { systemProperty("libfdx.test.captureFrame", it.toString()) }
}

val shaderGraphSpriteCapture =
    layout.buildDirectory.file("captures/shader-graph-sprite.ppm").get().asFile.absolutePath
val shaderGraphModelCapture =
    layout.buildDirectory.file("captures/shader-graph-model.ppm").get().asFile.absolutePath
val shaderGraphSkinnedModelCapture =
    layout.buildDirectory.file("captures/shader-graph-model-skinned.ppm").get().asFile.absolutePath
val shaderGraphComputeCapture =
    layout.buildDirectory.file("captures/shader-graph-compute.ppm").get().asFile.absolutePath
val uiCustomSurfaceCapture =
    layout.buildDirectory.file("captures/ui-custom-surface.ppm").get().asFile.absolutePath
val shaderGraphEditorCapture =
    layout.buildDirectory.file("captures/shader-graph-editor.ppm").get().asFile.absolutePath
val d3d12PbrStaticCapture =
    layout.buildDirectory.file("reports/shader-phase0/d3d12-smoke/static.ppm").get().asFile.absolutePath
val d3d12PbrSkinnedCapture =
    layout.buildDirectory.file("reports/shader-phase0/d3d12-smoke/skinned.ppm").get().asFile.absolutePath
val isWindowsHost = System.getProperty("os.name").lowercase().contains("windows")

libfdx {
    assets(rootProject.layout.projectDirectory.dir("tests/assets"))

    desktopJvm {
        mainClass.set("io.github.libfdx.tests.desktop.DesktopTestLauncher")
        workingDir.set(rootProject.layout.projectDirectory)
        minHeapSize.set("64m")
        maxHeapSize.set("1g")
        forwardSystemPropertyPrefix("libfdx.test.")
        forwardSystemPropertyPrefix("libfdx.validation.")

        target("d3d12") {
            displayName.set("Direct3D 12 graphics tests")
            runtimeClasspath(interactiveGraphicsRuntimeClasspath)
            graphics("d3d12", "Direct3D 12")
            runDescription.set("Runs graphics tests with Direct3D 12 through Java 25 FFM on Windows.")
        }
        target("gl") {
            displayName.set("GL graphics tests")
            runtimeClasspath(interactiveGraphicsRuntimeClasspath)
            graphics("gl", "GL")
            runDescription.set("Runs graphics tests with desktop GL.")
        }
        target("wgpu") {
            displayName.set("WGPU graphics tests")
            runtimeClasspath(interactiveGraphicsRuntimeClasspath)
            graphics("wgpu", "WGPU")
            runDescription.set("Runs graphics tests with WGPU.")
        }
        target("wgpu_compute") {
            displayName.set("WGPU handwritten compute test")
            runtimeClasspath(wgpuRuntimeClasspath)
            graphics("wgpu", "WGPU")
            headless("compute-buffer", 4, 320, 240)
            runDescription.set(
                "Dispatches and verifies a handwritten compute shader through the common graphics API."
            )
        }
        target("wgpu_render_targets") {
            displayName.set("WGPU render-target compatibility test")
            runtimeClasspath(wgpuRuntimeClasspath)
            graphics("wgpu", "WGPU")
            headless("render-target-compatibility", 3, 320, 240)
            runDescription.set(
                "Validates MRT, resolves, explicit depth, multisampling, and pipeline compatibility through WGPU."
            )
        }
        target("wgpu_shader_graph_program") {
            displayName.set("WGPU shader-graph program test")
            runtimeClasspath(wgpuRuntimeClasspath)
            graphics("wgpu", "WGPU")
            headless("shader-graph-program", 3, 320, 240)
            runDescription.set(
                "Compiles and renders a graph-owned vertex/fragment MRT program through ShaderProvider and WGPU."
            )
        }
        target("wgpu_shader_graph_technique") {
            displayName.set("WGPU shader-graph technique test")
            runtimeClasspath(wgpuRuntimeClasspath)
            graphics("wgpu", "WGPU")
            headless("shader-graph-technique", 3, 320, 240)
            runDescription.set(
                "Runs a multi-pass graph technique with variants, fallback, bounded caches, and atomic replacement."
            )
        }
        target("wgpu_shader_graph_sprite") {
            displayName.set("WGPU shader-graph sprite test")
            runtimeClasspath(wgpuRuntimeClasspath)
            graphics("wgpu", "WGPU")
            headless("shader-graph-sprite", 4, 640, 480, shaderGraphSpriteCapture, 2)
            runDescription.set(
                "Renders SpriteBatch through ShaderGraphProvider and every negotiated WGPU sprite ABI."
            )
        }
        target("wgpu_shader_graph_model") {
            displayName.set("WGPU shader-graph model test")
            runtimeClasspath(wgpuRuntimeClasspath)
            graphics("wgpu", "WGPU")
            headless("model", 32, 640, 480, shaderGraphModelCapture)
            systemProperty("libfdx.test.shaderGraphPbr", "true")
            runDescription.set("Renders static ModelBatch PBR through the common ShaderGraphProvider.")
        }
        target("wgpu_shader_graph_skinned_model") {
            displayName.set("WGPU shader-graph skinned-model test")
            runtimeClasspath(wgpuRuntimeClasspath)
            graphics("wgpu", "WGPU")
            headless("model-skinning", 46, 640, 480, shaderGraphSkinnedModelCapture, 44)
            systemProperty("libfdx.test.shaderGraphPbr", "true")
            runDescription.set("Renders skinned ModelBatch PBR through the common ShaderGraphProvider.")
        }
        target("wgpu_shader_graph_compute") {
            displayName.set("WGPU shader-graph compute test")
            runtimeClasspath(wgpuRuntimeClasspath)
            graphics("wgpu", "WGPU")
            headless("shader-graph-compute", 4, 320, 240, shaderGraphComputeCapture, 2)
            runDescription.set(
                "Dispatches graph-generated compute and compute-to-render programs through WGPU."
            )
        }
        target("wgpu_ui_custom_surface") {
            displayName.set("WGPU UI custom-surface test")
            runtimeClasspath(wgpuRuntimeClasspath)
            graphics("wgpu", "WGPU")
            headless("ui-custom-surface", 4, 960, 600, uiCustomSurfaceCapture, 2)
            runDescription.set(
                "Renders and captures UI Kit custom surfaces, clipping, lines, and retained paths through WGPU."
            )
        }
        target("wgpu_shader_graph_editor") {
            displayName.set("WGPU shader-graph editor test")
            runtimeClasspath(wgpuRuntimeClasspath)
            graphics("wgpu", "WGPU")
            headless("shader-graph-editor", 4, 1440, 900, shaderGraphEditorCapture, 2)
            runDescription.set(
                "Renders and captures the optional Shader Graph UI Kit editor through WGPU."
            )
        }
        target("vulkan") {
            displayName.set("Vulkan graphics tests")
            runtimeClasspath(interactiveGraphicsRuntimeClasspath)
            graphics("vulkan", "Vulkan")
            runDescription.set("Runs graphics tests with desktop Vulkan.")
        }
        target("d3d12_pbr_static_compile") {
            displayName.set("Direct3D 12 static PBR compile test")
            graphics("d3d12", "Direct3D 12")
            headless("model", 32, 640, 480, d3d12PbrStaticCapture)
            runDescription.set("Compiles and renders the static PBR shader through native D3D12 FXC.")
        }
        target("d3d12_pbr_skinned_compile") {
            displayName.set("Direct3D 12 skinned PBR compile test")
            graphics("d3d12", "Direct3D 12")
            headless("model-skinning", 46, 640, 480, d3d12PbrSkinnedCapture, 44)
            runDescription.set("Compiles and renders the skinned PBR shader through native D3D12 FXC.")
        }
        target("math_acceleration") {
            displayName.set("desktop runtime FDX SIMD math acceleration test")
            mainClass.set("io.github.libfdx.backend.desktop.DesktopMathAccelerationCheck")
            runtimeClasspath(runtimeFdxClasspath)
            systemProperty("libfdx.math.requireNative", "true")
            runDescription.set("Validates desktop runtime FDX SIMD math acceleration against scalar math.")
        }
    }
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

tasks.configureEach {
    if (name.startsWith("libfdx_desktop_jvm_") && name.endsWith("_run")) {
        finalizedBy(cleanTestRuntimeStorage)
    }
    if (name == "libfdx_desktop_jvm_d3d12_pbr_static_compile_run"
        || name == "libfdx_desktop_jvm_d3d12_pbr_skinned_compile_run") {
        onlyIf("Native D3D12 FXC is available only on Windows") { isWindowsHost }
    }
    if (name == "libfdx_desktop_jvm_d3d12_pbr_skinned_compile_run") {
        mustRunAfter("libfdx_desktop_jvm_d3d12_pbr_static_compile_run")
    }
}
