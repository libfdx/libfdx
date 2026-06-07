import io.github.libfdx.build.LibExt
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("project_generator_desktop")
}

group = "${LibExt.fdxGroup}.tools.projectgenerator"

dependencies {
    implementation(project(":libfdx:tools:project-generator:core"))
    implementation(project(":libfdx:tools:project-generator:ui"))
    implementation(project(":libfdx:backends:desktop"))
    runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop"))
}

val generatorMainClass = "io.github.libfdx.tools.project.generator.desktop.ProjectGeneratorDesktopLauncher"

fun JavaExec.configureGeneratorRun() {
    group = "application"
    description = "Runs the libfdx project generator desktop UI with GL."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(generatorMainClass)
    workingDir = rootProject.projectDir
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    jvmArgs("-Dorg.lwjgl.system.stackSize=1048576")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    listOf(
        "libfdx.projectGenerator.output",
        "libfdx.projectGenerator.exitAfterFrames",
        "libfdx.projectGenerator.visible"
    ).forEach { name ->
        System.getProperty(name)?.takeIf { it.isNotBlank() }?.let { value ->
            systemProperty(name, value)
        }
    }
}

tasks.register<JavaExec>("run_gl") {
    configureGeneratorRun()
}

tasks.register<JavaExec>("test_export_project") {
    group = "verification"
    description = "Runs the desktop project export smoke checks."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.libfdx.tools.project.generator.desktop.DesktopProjectExportSmokeTest")
    doFirst {
        systemProperty(
            "libfdx.projectGenerator.exportSmokeDir",
            layout.buildDirectory.dir("tmp/project-generator-export-smoke").get().asFile.absolutePath
        )
    }
}

tasks.named<Test>("test") {
    enabled = false
}

tasks.named("check") {
    dependsOn("test_export_project")
}
