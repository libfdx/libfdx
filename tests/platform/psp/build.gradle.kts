import io.github.libfdx.build.LibExt

import org.gradle.api.GradleException
import org.teavm.gradle.api.OptimizationLevel

plugins {
    id("java")
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.tests"


base {
    archivesName.set("tests_psp")
}

dependencies {
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_psp:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:g2d:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:ui_kit:${LibExt.publishedLibfdxVersion}")
    } else {
        implementation(project(":libfdx:backends:psp"))
        implementation(project(":libfdx:graphics:g2d"))
        implementation(project(":libfdx:ui:ui-kit"))
    }
}

val pspTestTasks = mapOf(
    "cube" to listOf("test_cube_generate", "test_cube_build", "test_cube_ppsspp_capture"),
    "spritebatch" to listOf("test_spritebatch_generate", "test_spritebatch_build", "test_spritebatch_ppsspp_capture"),
    "backend_clear" to listOf("test_backend_clear_generate", "test_backend_clear_build",
        "test_backend_clear_ppsspp_capture"),
    "backend_shape" to listOf("test_backend_shape_generate", "test_backend_shape_build",
        "test_backend_shape_ppsspp_capture"),
    "backend_ui_panel" to listOf("test_backend_ui_panel_generate", "test_backend_ui_panel_build",
        "test_backend_ui_panel_ppsspp_capture"),
    "backend_spritebatch" to listOf("test_backend_spritebatch_generate", "test_backend_spritebatch_build",
        "test_backend_spritebatch_ppsspp_capture"),
    "backend_input" to listOf("test_backend_input_generate", "test_backend_input_build",
        "test_backend_input_ppsspp_capture"),
    "backend_uikit" to listOf("test_backend_uikit_generate", "test_backend_uikit_build",
        "test_backend_uikit_ppsspp_capture"),
    "backend_uikit_smoke" to listOf("test_backend_uikit_smoke_generate", "test_backend_uikit_smoke_build",
        "test_backend_uikit_smoke_ppsspp_capture"),
)
val requestedTaskNames = gradle.startParameter.taskNames.map { it.substringAfterLast(":") }.toSet()
val selectedRequestedTests = pspTestTasks
    .filter { (_, tasks) -> tasks.any(requestedTaskNames::contains) }
    .keys
    .toList()
if (selectedRequestedTests.size > 1) {
    throw GradleException("Run PSP test variants in separate Gradle invocations: $selectedRequestedTests")
}
val defaultPspTest = selectedRequestedTests.firstOrNull() ?: "cube"
val selectedPspTest = providers.gradleProperty("libfdx.psp.test").orElse(defaultPspTest)

libfdx {
    assets(rootProject.file("tests/assets"))
    bitmapFont("psp_test_bitmap") {
        sourceFile.set(rootProject.layout.projectDirectory.file("tests/assets/font/freetype/lsans.ttf"))
        outputDir.set(rootProject.layout.projectDirectory.dir("tests/assets"))
        assetPath.set("font/bitmap")
        size.set(24)
        padding.set(2)
        maxTextureSize.set(512)
    }
    psp {
        mainClass.set(selectedPspTest.map { testName ->
            when (testName) {
                "cube" -> "io.github.libfdx.tests.psp.PspCubeTestLauncher"
                "spritebatch" -> "io.github.libfdx.tests.psp.PspSpriteBatchTestLauncher"
                "backend_clear" -> "io.github.libfdx.tests.psp.PspBackendClearTestLauncher"
                "backend_shape" -> "io.github.libfdx.tests.psp.PspBackendShapeTestLauncher"
                "backend_ui_panel" -> "io.github.libfdx.tests.psp.PspBackendUiPanelTestLauncher"
                "backend_spritebatch" -> "io.github.libfdx.tests.psp.PspBackendSpriteBatchTestLauncher"
                "backend_input" -> "io.github.libfdx.tests.psp.PspBackendInputTestLauncher"
                "backend_uikit" -> "io.github.libfdx.tests.psp.PspBackendUiKitTestLauncher"
                "backend_uikit_smoke" -> "io.github.libfdx.tests.psp.PspBackendUiKitSmokeTestLauncher"
                else -> throw GradleException("Unknown PSP test '$testName'. Use 'cube', 'spritebatch', 'backend_clear', 'backend_shape', 'backend_ui_panel', 'backend_spritebatch', 'backend_input', 'backend_uikit', or 'backend_uikit_smoke'.")
            }
        })
        targetFileName.set(selectedPspTest.map { testName ->
            when (testName) {
                "cube" -> "libfdx-tests-psp-cube"
                "spritebatch" -> "libfdx-psp-spritebatch"
                "backend_clear" -> "libfdx-psp-backend-clear"
                "backend_shape" -> "libfdx-psp-backend-shape"
                "backend_ui_panel" -> "libfdx-psp-backend-ui-panel"
                "backend_spritebatch" -> "libfdx-psp-backend2d"
                "backend_input" -> "libfdx-psp-backend-input"
                "backend_uikit" -> "libfdx-psp-backend-uikit"
                "backend_uikit_smoke" -> "libfdx-psp-backend-uikit-smoke"
                else -> throw GradleException("Unknown PSP test '$testName'. Use 'cube', 'spritebatch', 'backend_clear', 'backend_shape', 'backend_ui_panel', 'backend_spritebatch', 'backend_input', 'backend_uikit', or 'backend_uikit_smoke'.")
            }
        })
        optimization.set(OptimizationLevel.BALANCED)
        debugInformation.set(true)
        debugMemory.set(false)
        maxHeapSize.set(32)
    }
}

tasks.register("test_cube_generate") {
    group = "application"
    description = "Generates the libfdx PSP cube test TeaVM C project."
    dependsOn("libfdx_psp_generate")
}

tasks.register("test_cube_build") {
    group = "application"
    description = "Generates and builds the libfdx PSP cube test EBOOT project."
    dependsOn("libfdx_psp_build")
}

tasks.register("test_cube_ppsspp_capture") {
    group = "application"
    description = "Builds the libfdx PSP cube test and captures a PPSSPP emulator frame."
    dependsOn("libfdx_psp_ppsspp_capture")
}

tasks.register("test_spritebatch_generate") {
    group = "application"
    description = "Generates the libfdx PSP SpriteBatch test TeaVM C project."
    dependsOn("libfdx_psp_generate")
}

tasks.register("test_spritebatch_build") {
    group = "application"
    description = "Generates and builds the libfdx PSP SpriteBatch test EBOOT project."
    dependsOn("libfdx_psp_build")
}

tasks.register("test_spritebatch_ppsspp_capture") {
    group = "application"
    description = "Builds the libfdx PSP SpriteBatch test and captures a PPSSPP emulator frame."
    dependsOn("libfdx_psp_ppsspp_capture")
}

tasks.register("test_backend_clear_generate") {
    group = "application"
    description = "Generates the libfdx PSP ApplicationBackend clear-only test TeaVM C project."
    dependsOn("libfdx_psp_generate")
}

tasks.register("test_backend_clear_build") {
    group = "application"
    description = "Generates and builds the libfdx PSP ApplicationBackend clear-only test EBOOT project."
    dependsOn("libfdx_psp_build")
}

tasks.register("test_backend_clear_ppsspp_capture") {
    group = "application"
    description = "Builds the libfdx PSP ApplicationBackend clear-only test and captures a PPSSPP emulator frame."
    dependsOn("libfdx_psp_ppsspp_capture")
}

tasks.register("test_backend_shape_generate") {
    group = "application"
    description = "Generates the libfdx PSP ApplicationBackend shape-only test TeaVM C project."
    dependsOn("libfdx_psp_generate")
}

tasks.register("test_backend_shape_build") {
    group = "application"
    description = "Generates and builds the libfdx PSP ApplicationBackend shape-only test EBOOT project."
    dependsOn("libfdx_psp_build")
}

tasks.register("test_backend_shape_ppsspp_capture") {
    group = "application"
    description = "Builds the libfdx PSP ApplicationBackend shape-only test and captures a PPSSPP emulator frame."
    dependsOn("libfdx_psp_ppsspp_capture")
}

tasks.register("test_backend_ui_panel_generate") {
    group = "application"
    description = "Generates the libfdx PSP ApplicationBackend UIKit panel-only test TeaVM C project."
    dependsOn("libfdx_psp_generate")
}

tasks.register("test_backend_ui_panel_build") {
    group = "application"
    description = "Generates and builds the libfdx PSP ApplicationBackend UIKit panel-only test EBOOT project."
    dependsOn("libfdx_psp_build")
}

tasks.register("test_backend_ui_panel_ppsspp_capture") {
    group = "application"
    description = "Builds the libfdx PSP ApplicationBackend UIKit panel-only test and captures a PPSSPP emulator frame."
    dependsOn("libfdx_psp_ppsspp_capture")
}

tasks.register("test_backend_spritebatch_generate") {
    group = "application"
    description = "Generates the libfdx PSP ApplicationBackend asset SpriteBatch test TeaVM C project."
    dependsOn("libfdx_psp_generate")
}

tasks.register("test_backend_spritebatch_build") {
    group = "application"
    description = "Generates and builds the libfdx PSP ApplicationBackend asset SpriteBatch test EBOOT project."
    dependsOn("libfdx_psp_build")
}

tasks.register("test_backend_spritebatch_ppsspp_capture") {
    group = "application"
    description = "Builds the libfdx PSP ApplicationBackend asset SpriteBatch test and captures a PPSSPP emulator frame."
    dependsOn("libfdx_psp_ppsspp_capture")
}

tasks.register("test_backend_input_generate") {
    group = "application"
    description = "Generates the libfdx PSP ApplicationBackend input test TeaVM C project."
    dependsOn("libfdx_psp_generate")
}

tasks.register("test_backend_input_build") {
    group = "application"
    description = "Generates and builds the libfdx PSP ApplicationBackend input test EBOOT project."
    dependsOn("libfdx_psp_build")
}

tasks.register("test_backend_input_ppsspp_capture") {
    group = "application"
    description = "Builds the libfdx PSP ApplicationBackend input test and captures a PPSSPP emulator frame."
    dependsOn("libfdx_psp_ppsspp_capture")
}

tasks.register("test_backend_uikit_generate") {
    group = "application"
    description = "Generates the libfdx PSP ApplicationBackend UIKit test TeaVM C project."
    dependsOn("libfdx_psp_generate")
}

tasks.register("test_backend_uikit_build") {
    group = "application"
    description = "Generates and builds the libfdx PSP ApplicationBackend UIKit test EBOOT project."
    dependsOn("libfdx_psp_build")
}

tasks.register("test_backend_uikit_ppsspp_capture") {
    group = "application"
    description = "Builds the libfdx PSP ApplicationBackend UIKit test and captures a PPSSPP emulator frame."
    dependsOn("libfdx_psp_ppsspp_capture")
}

tasks.register("test_backend_uikit_smoke_generate") {
    group = "application"
    description = "Generates the scripted libfdx PSP ApplicationBackend UIKit smoke test TeaVM C project."
    dependsOn("libfdx_psp_generate")
}

tasks.register("test_backend_uikit_smoke_build") {
    group = "application"
    description = "Generates and builds the scripted libfdx PSP ApplicationBackend UIKit smoke test EBOOT project."
    dependsOn("libfdx_psp_build")
}

tasks.register("test_backend_uikit_smoke_ppsspp_capture") {
    group = "application"
    description = "Builds the scripted libfdx PSP ApplicationBackend UIKit smoke test and captures a PPSSPP emulator frame."
    dependsOn("libfdx_psp_ppsspp_capture")
}
