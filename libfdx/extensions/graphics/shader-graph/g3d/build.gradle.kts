plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
    withSourcesJar()
    withJavadocJar()
}

base {
    archivesName.set("shader_graph_g3d")
}

dependencies {
    api(project(":libfdx:extensions:graphics:shader-graph:runtime"))
    api(project(":libfdx:framework:g3d"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

val runtimeFdxBuildProject = project(":libfdx:framework:fdx:fdx-build")
val hostReflectClassifier = when {
    System.getProperty("os.name").lowercase().contains("win") ->
        "windows-x64/fdx_shaderc_reflect.exe"
    System.getProperty("os.name").lowercase().contains("mac")
            || System.getProperty("os.name").lowercase().contains("darwin") -> {
        val architecture = System.getProperty("os.arch").lowercase()
        val classifier = if (architecture == "aarch64" || architecture == "arm64") {
            "macos-arm64"
        } else {
            "macos-x64"
        }
        "$classifier/fdx_shaderc_reflect"
    }
    else -> "linux-x64/fdx_shaderc_reflect"
}
val hostReflectCli = runtimeFdxBuildProject.layout.buildDirectory.file(
    "generated/resources/runtimeFdxDesktop/libfdx-native/desktop/$hostReflectClassifier")

tasks.register<JavaExec>("validate_graph_pbr_tint") {
    group = "shader graph"
    description = "Validates graph-composed static and skinned PBR WGSL with Tint."
    dependsOn(tasks.named("testClasses"),
        ":libfdx:framework:fdx:fdx-build:build_runtime_fdx_host_reflect_cli")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.libfdx.graphics.g3d.PbrGraphTintTool")
    inputs.file(hostReflectCli)
    doFirst {
        args = listOf(hostReflectCli.get().asFile.absolutePath)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "shader_graph_g3d"
            from(components["java"])
        }
    }
}
