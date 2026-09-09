import io.github.libfdx.gradle.LibfdxDesktopJvmTargetExtension
import java.time.Duration
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

val wgpuJniRuntimeClasspath = configurations.create("wgpuJniRuntimeClasspath") {
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

//base {
//    archivesName.set("tests_desktop")
//}

dependencies {
    implementation(project(":tests:runner"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    implementation(project(":tests:core"))
    if (System.getProperty("libfdx.test.nativeWgpuAsync") == "true") {
        val bridge = System.getProperty("libfdx.test.wgpuBridge", "ffm")
        add(if (bridge == "jni") wgpuJniRuntimeClasspath.name else wgpuRuntimeClasspath.name,
            "com.github.xpenatan.jWebGPU:webgpu-desktop-$bridge-dawn_windows_x64:${libs.versions.jwebgpu.get()}")
    }
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_desktop:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:audio_openal:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:d3d12_core:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:wgpu_core:${libs.versions.libfdxSnapshot.get()}")

        glRuntimeClasspath("${libs.versions.libfdxGroup.get()}:gl_desktop:${libs.versions.libfdxSnapshot.get()}")
        vulkanRuntimeClasspath("${libs.versions.libfdxGroup.get()}:vulkan_desktop:${libs.versions.libfdxSnapshot.get()}")
        wgpuRuntimeClasspath("${libs.versions.libfdxGroup.get()}:wgpu_desktop_ffm:${libs.versions.libfdxSnapshot.get()}")
        wgpuJniRuntimeClasspath("${libs.versions.libfdxGroup.get()}:wgpu_desktop_jni:${libs.versions.libfdxSnapshot.get()}")
        runtimeFdxClasspath("${libs.versions.libfdxGroup.get()}:fdx_desktop:${libs.versions.libfdxSnapshot.get()}")
    } else {
        implementation(project(":libfdx:backends:desktop"))
        implementation(project(":libfdx:extensions:audio:openal"))
        implementation(project(":libfdx:extensions:graphics:d3d12:core"))
        implementation(project(":libfdx:extensions:graphics:wgpu:core"))

        glRuntimeClasspath(project(":libfdx:extensions:graphics:gl:platform:desktop"))
        vulkanRuntimeClasspath(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
        wgpuRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
        wgpuJniRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_jni"))
        runtimeFdxClasspath(project(":libfdx:framework:fdx:platform:desktop"))
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // Discover nested tests through their enclosing class, not callback/helper class files.
    // Optional native callback parents must not be loaded by ordinary JVM test discovery.
    exclude("**/*${'$'}*.class")
    val temporaryDirectory = layout.buildDirectory.dir("tmp/tests")
    systemProperty("java.io.tmpdir", temporaryDirectory.get().asFile.absolutePath)
    doFirst { temporaryDirectory.get().asFile.mkdirs() }
    if (System.getProperty("libfdx.test.nativeWgpuShaderFailure") == "true"
            || System.getProperty("libfdx.test.nativeWgpuPreparation") == "true"
            || System.getProperty("libfdx.test.nativeWgpuAsync") == "true") {
        val bridge = System.getProperty("libfdx.test.wgpuBridge", "ffm")
        classpath += when (bridge) {
            "ffm" -> wgpuRuntimeClasspath
            "jni" -> wgpuJniRuntimeClasspath
            else -> throw GradleException("libfdx.test.wgpuBridge must be ffm or jni")
        } + runtimeFdxClasspath
        systemProperty("libfdx.test.wgpuBridge", bridge)
        systemProperty("libfdx.test.nativeWgpuAsync", System.getProperty("libfdx.test.nativeWgpuAsync", "false"))
        systemProperty("libfdx.test.wgpuLoader", System.getProperty("libfdx.test.wgpuLoader", "DAWN"))
        val dawnPreparation = System.getProperty("libfdx.test.nativeDawnPreparation") == "true"
        systemProperty("libfdx.test.wgpuAsyncOutput", rootProject.layout.buildDirectory.dir(
            if (dawnPreparation) "native-dawn-preparation" else "dawn-snapshot-validation").get().asFile)
        if (dawnPreparation) workingDir(rootProject.projectDir)
        if (bridge == "jni") jvmArgs("-Xcheck:jni")
        systemProperty("libfdx.test.nativeWgpuShaderFailure", System.getProperty("libfdx.test.nativeWgpuShaderFailure", "false"))
        systemProperty("libfdx.test.nativeWgpuPreparation", System.getProperty("libfdx.test.nativeWgpuPreparation", "false"))
        systemProperty("libfdx.test.nativeDawnPreparation", System.getProperty("libfdx.test.nativeDawnPreparation", "false"))
        systemProperty("libfdx.test.wgpuFailureOutput", layout.buildDirectory.dir("wgpu-shader-failure").get().asFile)
        jvmArgs("--enable-native-access=ALL-UNNAMED")
        timeout.set(Duration.ofSeconds(60))
        testLogging.showStandardStreams = true
    }
}

fun LibfdxDesktopJvmTargetExtension.graphics(name: String, label: String) {
    systemProperty("libfdx.test.graphics", name)
    systemProperty("libfdx.test.graphicsLabel", label)
    launchProperty("graphics", name)
    launchProperty("graphicsLabel", label)
}

libfdx {
    assets(rootProject.layout.projectDirectory.dir("tests/assets"))

    desktopJvm {
        mainClass.set("io.github.libfdx.tests.desktop.DesktopTestLauncher")
        workingDir.set(rootProject.layout.projectDirectory)
        minHeapSize.set("64m")
        maxHeapSize.set("1g")
        forwardSystemPropertyPrefix("libfdx.test.")
        forwardSystemPropertyPrefix("libfdx.validation.")
        forwardSystemProperty("libfdx.profileFrames")

        target("tests_d3d12") {
            displayName.set("Direct3D 12 graphics tests")
            runtimeClasspath(interactiveGraphicsRuntimeClasspath)
            graphics("d3d12", "Direct3D 12")
            runDescription.set("Runs graphics tests with Direct3D 12 through Java 25 FFM on Windows.")
        }
        target("tests_auto") {
            displayName.set("All desktop graphics tests")
            runtimeClasspath(interactiveGraphicsRuntimeClasspath)
            systemProperty("libfdx.test.name", "auto")
            runDescription.set("Runs every chooser test across all desktop graphics providers with isolated processes and a checklist.")
        }
        target("tests_gl") {
            displayName.set("GL graphics tests")
            runtimeClasspath(interactiveGraphicsRuntimeClasspath)
            graphics("gl", "GL")
            runDescription.set("Runs graphics tests with desktop GL.")
        }
        target("tests_wgpu") {
            displayName.set("WGPU graphics tests")
            runtimeClasspath(interactiveGraphicsRuntimeClasspath)
            graphics("wgpu", "WGPU")
            runDescription.set("Runs graphics tests with WGPU.")
        }
        target("tests_vulkan") {
            displayName.set("Vulkan graphics tests")
            runtimeClasspath(interactiveGraphicsRuntimeClasspath)
            graphics("vulkan", "Vulkan")
            runDescription.set("Runs graphics tests with desktop Vulkan.")
        }
    }
}

tasks.register("validate_desktop_graphics") {
    group = "verification"
    description = "Runs the desktop test/API matrix and writes an incremental checklist."
    dependsOn("libfdx_desktop_jvm_tests_auto_run")
}

val cleanTestRuntimeStorage = tasks.register<Delete>("clean_test_runtime_storage") {
    group = "verification"
    description = "Removes the default persistent store created by StorageTest."
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
}
