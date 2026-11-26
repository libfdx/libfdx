plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("sample_shader_graph_core")
}

dependencies {
    api(project(":libfdx:extensions:graphics:shader-graph:core"))
    api(project(":libfdx:framework:application"))
    api(project(":libfdx:framework:g3d"))
    implementation(project(":libfdx:framework:input"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val sampleRoot = layout.projectDirectory.dir("..")

tasks.processResources {
    from(sampleRoot.dir("assets"))
}

tasks.register<JavaExec>("shader_graph_sample_generate_asset") {
    group = "sample"
    description = "Generates all Java-authored example .fdxgraph files in this module's build directory."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.github.libfdx.samples.shadergraph.ShaderGraphSampleGraphs")
    args(layout.buildDirectory.dir("generated-shader-graphs")
            .get().asFile.absolutePath)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    workingDir = sampleRoot.asFile
}
