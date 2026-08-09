
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
val canonicalPbrSource = layout.projectDirectory.file(
    "src/main/java/io/github/libfdx/graphics/g3d/PbrShaderProvider.java")
val canonicalPbrInterface = layout.projectDirectory.file(
    "src/main/java/io/github/libfdx/graphics/g3d/PbrShaderParameters.java")

tasks.register<JavaExec>("check_pbr_shader_interface") {
    group = "shader reflection"
    description = "Verifies the explicit PBR Java interface against fresh Tint reflection."
    dependsOn(tasks.named("testClasses"),
        ":libfdx:framework:fdx:fdx-build:build_runtime_fdx_host_reflect_cli")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.libfdx.graphics.g3d.PbrShaderInterfaceTool")
    inputs.file(hostReflectCli)
    inputs.file(canonicalPbrSource)
    inputs.file(canonicalPbrInterface)
    doFirst {
        args = listOf(hostReflectCli.get().asFile.absolutePath)
    }
}

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
