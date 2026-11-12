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
    api(project(":libfdx:framework:application"))
    implementation(project(":libfdx:framework:camera"))
    implementation(project(":libfdx:framework:files"))
    implementation(project(":libfdx:framework:graphics"))
    implementation(project(":libfdx:framework:g2d"))
    implementation(project(":libfdx:framework:g3d"))
    implementation(project(":libfdx:framework:input"))
    implementation(project(":libfdx:framework:math"))
    implementation(project(":libfdx:extensions:graphics:shader-graph:core"))
    implementation(project(":libfdx:extensions:graphics:shader-graph:runtime"))
    implementation(project(":libfdx:extensions:graphics:shader-graph:g2d"))
    implementation(project(":libfdx:extensions:graphics:shader-graph:g3d"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val sampleRoot = layout.projectDirectory.dir("..")

tasks.processResources {
    from(sampleRoot.dir("assets"))
}

tasks.register<JavaExec>("shader_graph_sample_generate_asset") {
    group = "sample"
    description = "Generates the code-authored sample .fdxgraph into this module's build directory."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.github.libfdx.samples.shadergraph.ShaderGraphSampleAssetGenerator")
    args(layout.buildDirectory.file("generated-shader-graphs/warm-pbr-surface.fdxgraph")
            .get().asFile.absolutePath)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    workingDir = sampleRoot.asFile
}
