plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

val moduleName = "shader_graph_core"

base {
    archivesName.set(moduleName)
}

dependencies {
    api(project(":libfdx:framework:graphics"))
    api(project(":libfdx:framework:json"))
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

tasks.register<JavaExec>("validate_shader_graph_tint") {
    group = "shader graph"
    description = "Validates canonical graph-generated WGSL with the native Tint bridge."
    dependsOn(tasks.named("testClasses"),
        ":libfdx:framework:fdx:fdx-build:build_runtime_fdx_host_reflect_cli")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphTintTool")
    inputs.file(hostReflectCli)
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
