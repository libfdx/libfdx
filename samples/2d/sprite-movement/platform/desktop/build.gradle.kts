
import org.gradle.api.attributes.java.TargetJvmVersion
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

base {
    archivesName.set("sample_2d_sprite_movement_desktop")
}

dependencies {
    implementation(project(":samples:2d:sprite-movement:core"))
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:application:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:display:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:d3d12_core:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:wgpu_core:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:backend_desktop:${libs.versions.libfdxSnapshot.get()}")

        glRuntimeClasspath("${libs.versions.libfdxGroup.get()}:gl_desktop:${libs.versions.libfdxSnapshot.get()}")
        vulkanRuntimeClasspath("${libs.versions.libfdxGroup.get()}:vulkan_desktop:${libs.versions.libfdxSnapshot.get()}")
        wgpuRuntimeClasspath("${libs.versions.libfdxGroup.get()}:wgpu_desktop_ffm:${libs.versions.libfdxSnapshot.get()}")
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

val sampleMainClass = "io.github.libfdx.samples.g2d.spritemovement.desktop.SpriteMovementDesktopLauncher"
val sampleRoot = rootProject.file("samples/2d/sprite-movement")

fun registerGraphicsRun(
    taskName: String,
    graphics: String,
    graphicsLabel: String,
    providerClasspath: FileCollection
) = tasks.register<JavaExec>(taskName) {
    group = "application"
    description = "Runs the 2D Sprite Movement desktop sample with $graphicsLabel."
    classpath = sourceSets["main"].runtimeClasspath + providerClasspath
    mainClass.set(sampleMainClass)
    workingDir = sampleRoot
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

registerGraphicsRun("sprite_movement_desktop_gl_run", "gl", "GL", glRuntimeClasspath)
registerGraphicsRun("sprite_movement_desktop_wgpu_run", "wgpu", "WGPU", wgpuRuntimeClasspath)
registerGraphicsRun("sprite_movement_desktop_vulkan_run", "vulkan", "Vulkan", vulkanRuntimeClasspath)
registerGraphicsRun("sprite_movement_desktop_d3d12_run", "d3d12", "Direct3D 12", files())
