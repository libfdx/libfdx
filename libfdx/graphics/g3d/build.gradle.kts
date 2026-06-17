import io.github.libfdx.build.LibExt

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.graphics"

base {
    archivesName.set("g3d")
}

val generatedShaderSources = layout.buildDirectory.dir("generated/sources/libfdxShaders/java/main")
val shaderSources = layout.projectDirectory.dir("src/main/shaders")
val shaderToolProject = project(":libfdx:tools:shader:core")
val shaderToolClasspath by configurations.creating

sourceSets {
    main {
        java.srcDir(generatedShaderSources)
    }
}

val generateG3dShaderBundles = tasks.register<JavaExec>("generate_g3d_shader_bundles") {
    group = "libfdx shaders"
    description = "Generates built-in g3d shader bundle Java sources."
    dependsOn(":libfdx:tools:shader:core:classes")
    dependsOn(":libfdx:tools:shader:core:build_shaderc_host")
    classpath = shaderToolClasspath
    mainClass.set("io.github.libfdx.tools.shader.FdxShaderJavaBundleGeneratorMain")
    inputs.dir(shaderSources)
    inputs.files(shaderToolClasspath)
    outputs.dir(generatedShaderSources)
    args(
        "--compiler-dir", shaderToolProject.layout.buildDirectory.dir("native/shaderc/host").get().asFile.absolutePath,
        "--output", generatedShaderSources.get().asFile.absolutePath,
        "--package", "io.github.libfdx.graphics.g3d.generated",
        "--class", "GeneratedModelBatchShaders",
        "--shader", "positionColor|model batch position color|${shaderSources.file("model_batch_position_color.wgsl").asFile.absolutePath}",
        "--shader", "pbr|model batch pbr|${shaderSources.file("model_batch_pbr.wgsl").asFile.absolutePath}"
    )
}

tasks.named("compileJava") {
    dependsOn(generateG3dShaderBundles)
}

tasks.matching { it.name == "sourcesJar" }.configureEach {
    dependsOn(generateG3dShaderBundles)
}

dependencies {
    api(project(":libfdx:graphics:api"))
    api(project(":libfdx:foundation:math"))
    implementation(project(":libfdx:foundation:json"))
    api(project(":libfdx:assets:manager"))
    api(project(":libfdx:assets:loaders"))
    shaderToolClasspath(project(":libfdx:tools:shader:core"))
}
