import io.github.libfdx.build.LibExt

import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.tests"

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
    archivesName.set("tests_desktop")
}

dependencies {
    implementation(project(":tests:core"))
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_desktop:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:wgpu_core:${LibExt.publishedLibfdxVersion}")

        glRuntimeClasspath("${LibExt.fdxGroup}:gl_desktop:${LibExt.publishedLibfdxVersion}")
        vulkanRuntimeClasspath("${LibExt.fdxGroup}:vulkan_desktop:${LibExt.publishedLibfdxVersion}")
        wgpuRuntimeClasspath("${LibExt.fdxGroup}:wgpu_desktop_ffm:${LibExt.publishedLibfdxVersion}")
    } else {
        implementation(project(":libfdx:backends:desktop"))
        implementation(project(":libfdx:extensions:graphics:wgpu:core"))

        glRuntimeClasspath(project(":libfdx:extensions:graphics:gl:platform:desktop"))
        vulkanRuntimeClasspath(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
        wgpuRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
    }
}

val testRuntimeClasspath = sourceSets["main"].runtimeClasspath +
        glRuntimeClasspath + wgpuRuntimeClasspath + vulkanRuntimeClasspath

val desktopBackendResources = if (LibExt.usePublishedLibfdx) {
    files()
} else {
    files(project(":libfdx:backends:desktop").layout.buildDirectory.dir("resources/main"))
}

tasks.register<JavaExec>("test_desktop_gl_run") {
    group = "application"
    description = "Runs graphics tests with desktop GL."
    classpath = testRuntimeClasspath
    mainClass.set("io.github.libfdx.tests.desktop.DesktopTestLauncher")
    systemProperty("libfdx.test.graphics", "gl")
    systemProperty("libfdx.test.graphicsLabel", "GL")
}

tasks.register<JavaExec>("test_desktop_wgpu_run") {
    group = "application"
    description = "Runs graphics tests with WGPU."
    classpath = testRuntimeClasspath
    mainClass.set("io.github.libfdx.tests.desktop.DesktopTestLauncher")
    systemProperty("libfdx.test.graphics", "wgpu")
    systemProperty("libfdx.test.graphicsLabel", "WGPU")
}

tasks.register<JavaExec>("test_desktop_vulkan_run") {
    group = "application"
    description = "Runs graphics tests with desktop Vulkan."
    classpath = testRuntimeClasspath
    mainClass.set("io.github.libfdx.tests.desktop.DesktopTestLauncher")
    systemProperty("libfdx.test.graphics", "vulkan")
    systemProperty("libfdx.test.graphicsLabel", "Vulkan")
}

tasks.register<JavaExec>("test_math_acceleration_desktop") {
    group = "application_test"
    description = "Validates desktop runtime fdx SIMD math acceleration against scalar math."
    if (!LibExt.usePublishedLibfdx) {
        dependsOn(":libfdx:backends:desktop:processResources")
    }
    classpath = sourceSets["main"].output + sourceSets["main"].compileClasspath + desktopBackendResources
    mainClass.set("io.github.libfdx.backend.desktop.DesktopMathAccelerationCheck")
    systemProperty("libfdx.math.requireNative", "true")
}

tasks.withType<JavaExec>().configureEach {
    workingDir = rootProject.projectDir
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    jvmArgs("-Dorg.lwjgl.system.stackSize=1048576", "--enable-native-access=ALL-UNNAMED")
    gradle.startParameter.systemPropertiesArgs
            .filterKeys {
                (it.startsWith("libfdx.test.") || it.startsWith("libfdx.validation."))
                        && it != "libfdx.test.graphics"
                        && it != "libfdx.test.graphicsLabel"
            }
            .filterValues { it.isNotBlank() }
            .forEach { (name, value) -> systemProperty(name, value) }
}
