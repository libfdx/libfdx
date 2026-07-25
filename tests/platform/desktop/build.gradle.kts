
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
val vulkanRun = registerGraphicsTestRun(
    "test_desktop_vulkan_run",
    "Runs graphics tests with desktop Vulkan.",
    "vulkan",
    "Vulkan",
    vulkanRuntimeClasspath
)

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

listOf(d3d12Run, glRun, wgpuRun, vulkanRun).forEach { runTask ->
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
