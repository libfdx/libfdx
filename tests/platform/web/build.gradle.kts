plugins {
    id("io.github.libfdx")
}

val graphicsMatrixRunner = configurations.create("graphicsMatrixRunner") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE, objects.named(org.gradle.api.attributes.Usage.JAVA_RUNTIME))
        attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}
dependencies {
    add(graphicsMatrixRunner.name, project(":tests:runner"))
    add(graphicsMatrixRunner.name, libs.playwright)
}

tasks.register<JavaExec>("validate_web_graphics") {
    group = "verification"
    description = "Builds and validates all tests on JS and Wasm with WebGL and WebGPU in isolated browsers."
    dependsOn("libfdx_web_js_webgl_build", "libfdx_web_js_webgpu_build", "libfdx_web_wasm_webgl_build", "libfdx_web_wasm_webgpu_build")
    classpath = graphicsMatrixRunner
    mainClass.set("io.github.libfdx.testsupport.runner.PlatformMatrixLauncher")
    workingDir(rootProject.projectDir)
    systemProperties(gradle.startParameter.systemPropertiesArgs.filterKeys { it.startsWith("libfdx.test.") })
    systemProperty("libfdx.test.autoPlatform", "web")
    environment("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")
    environment("PLAYWRIGHT_BROWSERS_PATH", layout.buildDirectory.dir("browsers").get().asFile.absolutePath)
    doFirst {
        val temporaryDirectory = layout.buildDirectory.dir("tmp/browser-runner").get().asFile
        temporaryDirectory.mkdirs()
        systemProperty("java.io.tmpdir", temporaryDirectory.absolutePath)
        systemProperty("libfdx.test.autoWebJsDirectory", tasks.named<io.github.libfdx.gradle.LibfdxRunWebTask>("libfdx_web_js_webgl_run").get().webappDir.get().asFile.absolutePath)
        systemProperty("libfdx.test.autoWebWasmDirectory", tasks.named<io.github.libfdx.gradle.LibfdxRunWebTask>("libfdx_web_wasm_webgl_run").get().webappDir.get().asFile.absolutePath)
    }
}

tasks.register<JavaExec>("install_test_browser") {
    group = "verification"
    description = "Downloads the Chromium version used by the web graphics validation task."
    classpath = graphicsMatrixRunner
    mainClass.set("com.microsoft.playwright.CLI")
    args("install", "chromium")
    environment("PLAYWRIGHT_BROWSERS_PATH", layout.buildDirectory.dir("browsers").get().asFile.absolutePath)
    doFirst {
        val temporaryDirectory = layout.buildDirectory.dir("tmp/browser-runner").get().asFile
        temporaryDirectory.mkdirs()
        systemProperty("java.io.tmpdir", temporaryDirectory.absolutePath)
    }
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("tests_web")
}

dependencies {
    implementation(project(":tests:core"))
    implementation(libs.teavm.jso)
    implementation(libs.teavm.jso.apis)
    implementation(libs.teavm.jso.impl)
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_web:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:audio_web:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:gl_web:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:wgpu_web:${libs.versions.libfdxSnapshot.get()}")
    } else {
        implementation(project(":libfdx:backends:web"))
        implementation(project(":libfdx:extensions:audio:web"))
        implementation(project(":libfdx:extensions:graphics:gl:platform:web"))
        implementation(project(":libfdx:extensions:graphics:wgpu:platform:web"))
    }
}

libfdx {
    assets(rootProject.layout.projectDirectory.dir("tests/assets"))

    js {
        mainClass.set("io.github.libfdx.tests.web.WebTestJsLauncher")
        htmlTitle.set("libfdx Tests - WebGL JS")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)

        target("tests_webgl") {
            buildDescription.set("Builds the WebGL JavaScript test web application.")
            runDescription.set("Builds and serves the WebGL JavaScript test web application.")
        }
        target("tests_webgpu") {
            defaultPath.set("/?graphics=webgpu")
            buildDescription.set("Builds the WebGPU JavaScript test web application.")
            runDescription.set("Builds and serves the WebGPU JavaScript test web application.")
        }
    }
    wasm {
        mainClass.set("io.github.libfdx.tests.web.WebTestWasmLauncher")
        htmlTitle.set("libfdx Tests - WebGL Wasm")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)

        target("tests_webgl") {
            buildDescription.set("Builds the WebGL Wasm test web application.")
            runDescription.set("Builds and serves the WebGL Wasm test web application.")
        }
        target("tests_webgpu") {
            defaultPath.set("/?graphics=webgpu")
            buildDescription.set("Builds the WebGPU Wasm test web application.")
            runDescription.set("Builds and serves the WebGPU Wasm test web application.")
        }
    }
}
