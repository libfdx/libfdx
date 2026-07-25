
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar
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


dependencies {
    implementation(project(":libfdx:tools:project-generator:core"))
    implementation(project(":libfdx:tools:project-generator:ui"))
    implementation(project(":libfdx:backends:desktop"))
    runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop"))
}

val generatorMainClass = "io.github.libfdx.tools.project.generator.desktop.ProjectGeneratorDesktopLauncher"
val generatorReleaseClasspath = sourceSets["main"].runtimeClasspath
val generatorDesktopDistDir = layout.buildDirectory.dir("dist/desktop-jvm")

val projectGeneratorDesktopGlBuild = tasks.register<Jar>("project_generator_desktop_gl_build") {
    group = "application"
    description = "Builds the libfdx project generator desktop GL release jar."
    dependsOn("classes", generatorReleaseClasspath)
    archiveFileName.set("project_generator_desktop_gl.jar")
    destinationDirectory.set(generatorDesktopDistDir)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    isZip64 = true
    manifest {
        attributes(
                "Main-Class" to generatorMainClass,
                "Multi-Release" to "true",
                "Enable-Native-Access" to "ALL-UNNAMED")
    }
    exclude("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF")
    from({
        generatorReleaseClasspath.files
                .filter { it.exists() }
                .map { if (it.isDirectory) it else zipTree(it) }
    })
}

tasks.register<JavaExec>("project_generator_desktop_gl_run") {
    group = "application"
    description = "Runs the libfdx project generator desktop UI with GL."
    dependsOn(projectGeneratorDesktopGlBuild)
    classpath = generatorReleaseClasspath
    mainClass.set(generatorMainClass)
    workingDir = rootProject.projectDir
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    jvmArgs("-Dorg.lwjgl.system.stackSize=1024", "--enable-native-access=ALL-UNNAMED")
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
