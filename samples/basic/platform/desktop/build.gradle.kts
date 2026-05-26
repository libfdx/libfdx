import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("java")
}

group = "io.github.libfdx.samples.basic"

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
    implementation(project(":libfdx:runtime:application"))
    implementation(project(":libfdx:runtime:display"))
    implementation(project(":libfdx:extensions:graphics:wgpu:core"))
    implementation(project(":libfdx:backends:desktop"))

    glRuntimeClasspath(project(":libfdx:extensions:graphics:gl:platform:desktop"))
    vulkanRuntimeClasspath(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
    wgpuRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
}

val sampleMainClass = "io.github.libfdx.samples.basic.desktop.BasicDesktopLauncher"

fun JavaExec.configureSampleRun(
    descriptionText: String,
    graphics: String,
    graphicsLabel: String,
    providerClasspath: FileCollection
) {
    group = "application"
    description = descriptionText
    classpath = sourceSets["main"].runtimeClasspath + providerClasspath
    mainClass.set(sampleMainClass)
    workingDir = rootProject.projectDir
    val exitAfterFrames = System.getProperty("libfdx.sample.exitAfterFrames")
    if (!exitAfterFrames.isNullOrBlank()) {
        systemProperty("libfdx.sample.exitAfterFrames", exitAfterFrames)
    }
    systemProperty("libfdx.sample.graphics", graphics)
    systemProperty("libfdx.sample.graphicsLabel", graphicsLabel)
    jvmArgs("-Dorg.lwjgl.system.stackSize=1048576")
    if (JavaVersion.current().majorVersion.toInt() >= 22) {
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}

fun JavaExec.useJava25Launcher() {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.register<JavaExec>("run_wgpu") {
    configureSampleRun("Runs the basic desktop sample with WGPU.", "wgpu", "WGPU", wgpuRuntimeClasspath)
    useJava25Launcher()
}

tasks.register<JavaExec>("run_gl") {
    configureSampleRun("Runs the basic desktop sample with GL.", "gl", "GL", glRuntimeClasspath)
    useJava25Launcher()
}

tasks.register<JavaExec>("run_vulkan") {
    configureSampleRun("Runs the basic desktop sample with Vulkan.", "vulkan", "Vulkan", vulkanRuntimeClasspath)
    useJava25Launcher()
}
