
plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}


val moduleName = "g3d"

base {
    archivesName.set(moduleName)
}

dependencies {
    api(project(":libfdx:framework:collections"))
    api(project(":libfdx:framework:graphics"))
    api(project(":libfdx:extensions:graphics:shader-graph:runtime"))
    api(project(":libfdx:framework:camera"))
    api(project(":libfdx:framework:math"))
    implementation(project(":libfdx:framework:json"))
    api(project(":libfdx:framework:assets:manager"))
    api(project(":libfdx:framework:assets:loaders"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

val runtimeFdxBuildProject = project(":libfdx:framework:fdx:fdx-build")
val hostReflectClassifier = when {
    System.getProperty("os.name").lowercase().contains("win") -> "windows-x64/fdx_shaderc_reflect.exe"
    System.getProperty("os.name").lowercase().contains("mac")
            || System.getProperty("os.name").lowercase().contains("darwin") -> {
        val architecture = System.getProperty("os.arch").lowercase()
        val classifier = if (architecture == "aarch64" || architecture == "arm64") "macos-arm64" else "macos-x64"
        "$classifier/fdx_shaderc_reflect"
    }
    else -> "linux-x64/fdx_shaderc_reflect"
}
val hostReflectCli = runtimeFdxBuildProject.layout.buildDirectory.file(
    "generated/resources/runtimeFdxDesktop/libfdx-native/desktop/$hostReflectClassifier")
val generatedPbrManifest = rootProject.layout.projectDirectory.file(
    "libfdx/framework/graphics/src/main/java/io/github/libfdx/graphics/internal/"
            + "GeneratedPbrShaderManifestData.java")
val canonicalPbrSource = layout.projectDirectory.file(
    "src/main/java/io/github/libfdx/graphics/g3d/PbrShaderProvider.java")

fun registerPbrManifestTask(name: String, mode: String) = tasks.register<JavaExec>(name) {
    group = "shader reflection"
    description = if (mode == "check") {
        "Verifies the built-in PBR FDXI payloads against the canonical WGSL."
    } else {
        "Regenerates the built-in PBR FDXI payloads from the canonical WGSL."
    }
    dependsOn(tasks.named("testClasses"),
        ":libfdx:framework:fdx:fdx-build:build_runtime_fdx_host_reflect_cli")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.libfdx.graphics.g3d.PbrShaderManifestTool")
    inputs.property("mode", mode)
    inputs.file(hostReflectCli)
    inputs.file(canonicalPbrSource)
    if (mode == "check") {
        inputs.file(generatedPbrManifest)
    } else {
        outputs.file(generatedPbrManifest)
    }
    doFirst {
        args = listOf(
            hostReflectCli.get().asFile.absolutePath,
            generatedPbrManifest.asFile.absolutePath,
            mode,
        )
    }
}

registerPbrManifestTask("generate_pbr_shader_manifest", "generate")
registerPbrManifestTask("check_pbr_shader_manifest", "check")

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = moduleName
            from(components["java"])
        }
    }
}
