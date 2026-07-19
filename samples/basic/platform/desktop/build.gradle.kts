import io.github.libfdx.build.LibExt

import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("java")
}

group = "${LibExt.fdxGroup}.samples.basic"


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

base {
    archivesName.set("sample_basic_desktop")
}

dependencies {
    implementation(project(":samples:basic:core"))
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:application:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:display:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:d3d12_core:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:wgpu_core:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:backend_desktop:${LibExt.fdxSnapshotVersion}")

        glRuntimeClasspath("${LibExt.fdxGroup}:gl_desktop:${LibExt.fdxSnapshotVersion}")
        vulkanRuntimeClasspath("${LibExt.fdxGroup}:vulkan_desktop:${LibExt.fdxSnapshotVersion}")
        wgpuRuntimeClasspath("${LibExt.fdxGroup}:wgpu_desktop_ffm:${LibExt.fdxSnapshotVersion}")
    } else {
        implementation(project(":libfdx:framework:application"))
        implementation(project(":libfdx:framework:display"))
        implementation(project(":libfdx:extensions:graphics:d3d12:core"))
        implementation(project(":libfdx:extensions:graphics:wgpu:core"))
        implementation(project(":libfdx:backends:desktop"))

        glRuntimeClasspath(project(":libfdx:extensions:graphics:gl:platform:desktop"))
        vulkanRuntimeClasspath(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
        wgpuRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
    }
}

val sampleMainClass = "io.github.libfdx.samples.basic.desktop.BasicDesktopLauncher"

fun registerGraphicsRun(
    taskName: String,
    graphics: String,
    graphicsLabel: String,
    providerClasspath: FileCollection
) = tasks.register<JavaExec>(taskName) {
    group = "application"
    description = "Runs the basic desktop sample with $graphicsLabel."
    classpath = sourceSets["main"].runtimeClasspath + providerClasspath
    mainClass.set(sampleMainClass)
    workingDir = rootProject.projectDir
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    jvmArgs("-Dorg.lwjgl.system.stackSize=1024", "--enable-native-access=ALL-UNNAMED")
    systemProperty("libfdx.sample.graphics", graphics)
    systemProperty("libfdx.sample.graphicsLabel", graphicsLabel)
    System.getProperty("libfdx.sample.exitAfterFrames")?.takeIf { it.isNotBlank() }?.let { frames ->
        systemProperty("libfdx.sample.exitAfterFrames", frames)
    }
    System.getProperty("libfdx.sample.maximized")?.takeIf { it.isNotBlank() }?.let { maximized ->
        systemProperty("libfdx.sample.maximized", maximized)
    }
}

registerGraphicsRun("basic_desktop_gl_run", "gl", "GL", glRuntimeClasspath)
registerGraphicsRun("basic_desktop_wgpu_run", "wgpu", "WGPU", wgpuRuntimeClasspath)
registerGraphicsRun("basic_desktop_vulkan_run", "vulkan", "Vulkan", vulkanRuntimeClasspath)
registerGraphicsRun("basic_desktop_d3d12_run", "d3d12", "Direct3D 12", files())
