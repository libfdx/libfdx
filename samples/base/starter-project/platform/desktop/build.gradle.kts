import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("java")
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

base {
    archivesName.set("sample_base_starter_project_desktop")
}

val sampleProjectPath = project.path.substringBefore(":platform:")
val libfdxDependencyVersion =
    gradle.extensions.extraProperties.get("libfdxDependencyVersion") as String

dependencies {
    implementation(project("$sampleProjectPath:core"))
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:application:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:display:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:d3d12_core:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:wgpu_core:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:backend_desktop:$libfdxDependencyVersion")

        glRuntimeClasspath("${libs.versions.libfdxGroup.get()}:gl_desktop:$libfdxDependencyVersion")
        vulkanRuntimeClasspath("${libs.versions.libfdxGroup.get()}:vulkan_desktop:$libfdxDependencyVersion")
        wgpuRuntimeClasspath("${libs.versions.libfdxGroup.get()}:wgpu_desktop_ffm:$libfdxDependencyVersion")
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

val sampleMainClass = "io.github.libfdx.samples.starter.desktop.StarterProjectDesktopLauncher"
val sampleRoot = layout.projectDirectory.dir("../..").asFile

fun registerGraphicsRun(
    taskName: String,
    graphics: String,
    graphicsLabel: String,
    providerClasspath: FileCollection
) = tasks.register<JavaExec>(taskName) {
    group = "application"
    description = "Runs the Starter Project desktop sample with $graphicsLabel."
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

registerGraphicsRun("starter_project_desktop_gl_run", "gl", "GL", glRuntimeClasspath)
registerGraphicsRun("starter_project_desktop_wgpu_run", "wgpu", "WGPU", wgpuRuntimeClasspath)
registerGraphicsRun("starter_project_desktop_vulkan_run", "vulkan", "Vulkan", vulkanRuntimeClasspath)
registerGraphicsRun("starter_project_desktop_d3d12_run", "d3d12", "Direct3D 12", files())
