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
        implementation("${LibExt.fdxGroup}:application:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:display:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:wgpu_core:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:backend_desktop:${LibExt.publishedLibfdxVersion}")

        glRuntimeClasspath("${LibExt.fdxGroup}:gl_desktop:${LibExt.publishedLibfdxVersion}")
        vulkanRuntimeClasspath("${LibExt.fdxGroup}:vulkan_desktop:${LibExt.publishedLibfdxVersion}")
        wgpuRuntimeClasspath("${LibExt.fdxGroup}:wgpu_desktop_ffm:${LibExt.publishedLibfdxVersion}")
    } else {
        implementation(project(":libfdx:runtime:application"))
        implementation(project(":libfdx:runtime:display"))
        implementation(project(":libfdx:extensions:graphics:wgpu:core"))
        implementation(project(":libfdx:backends:desktop"))

        glRuntimeClasspath(project(":libfdx:extensions:graphics:gl:platform:desktop"))
        vulkanRuntimeClasspath(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
        wgpuRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
    }
}

val sampleMainClass = "io.github.libfdx.samples.basic.desktop.BasicDesktopLauncher"

tasks.register<JavaExec>("basic_desktop_gl_run") {
    group = "application"
    description = "Runs the basic desktop sample with GL."
    classpath = sourceSets["main"].runtimeClasspath + glRuntimeClasspath
    mainClass.set(sampleMainClass)
    workingDir = rootProject.projectDir
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    jvmArgs("-Dorg.lwjgl.system.stackSize=1048576", "--enable-native-access=ALL-UNNAMED")
    systemProperty("libfdx.sample.graphics", "gl")
    systemProperty("libfdx.sample.graphicsLabel", "GL")
    System.getProperty("libfdx.sample.exitAfterFrames")?.takeIf { it.isNotBlank() }?.let { frames ->
        systemProperty("libfdx.sample.exitAfterFrames", frames)
    }
}

tasks.register<JavaExec>("basic_desktop_wgpu_run") {
    group = "application"
    description = "Runs the basic desktop sample with WGPU."
    classpath = sourceSets["main"].runtimeClasspath + wgpuRuntimeClasspath
    mainClass.set(sampleMainClass)
    workingDir = rootProject.projectDir
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    jvmArgs("-Dorg.lwjgl.system.stackSize=1048576", "--enable-native-access=ALL-UNNAMED")
    systemProperty("libfdx.sample.graphics", "wgpu")
    systemProperty("libfdx.sample.graphicsLabel", "WGPU")
    System.getProperty("libfdx.sample.exitAfterFrames")?.takeIf { it.isNotBlank() }?.let { frames ->
        systemProperty("libfdx.sample.exitAfterFrames", frames)
    }
}

tasks.register<JavaExec>("basic_desktop_vulkan_run") {
    group = "application"
    description = "Runs the basic desktop sample with Vulkan."
    classpath = sourceSets["main"].runtimeClasspath + vulkanRuntimeClasspath
    mainClass.set(sampleMainClass)
    workingDir = rootProject.projectDir
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    jvmArgs("-Dorg.lwjgl.system.stackSize=1048576", "--enable-native-access=ALL-UNNAMED")
    systemProperty("libfdx.sample.graphics", "vulkan")
    systemProperty("libfdx.sample.graphicsLabel", "Vulkan")
    System.getProperty("libfdx.sample.exitAfterFrames")?.takeIf { it.isNotBlank() }?.let { frames ->
        systemProperty("libfdx.sample.exitAfterFrames", frames)
    }
}
