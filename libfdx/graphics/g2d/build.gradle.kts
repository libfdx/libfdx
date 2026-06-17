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
    archivesName.set("g2d")
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

val generateG2dShaderBundles = tasks.register<JavaExec>("generate_g2d_shader_bundles") {
    group = "libfdx shaders"
    description = "Generates built-in g2d shader bundle Java sources."
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
        "--package", "io.github.libfdx.graphics.g2d.generated",
        "--class", "GeneratedSpriteBatchShaders",
        "--shader", "spriteBatch|sprite batch|${shaderSources.file("sprite_batch.wgsl").asFile.absolutePath}",
        "--shader", "whiteSpriteBatch|white sprite batch|${shaderSources.file("white_sprite_batch.wgsl").asFile.absolutePath}",
        "--shader", "instancedSpriteBatch|instanced sprite batch|${shaderSources.file("instanced_sprite_batch.wgsl").asFile.absolutePath}",
        "--shader", "compactInstancedSpriteBatch|compact instanced sprite batch|${shaderSources.file("compact_instanced_sprite_batch.wgsl").asFile.absolutePath}"
    )
}

tasks.named("compileJava") {
    dependsOn(generateG2dShaderBundles)
}

dependencies {
    api(project(":libfdx:graphics:api"))
    api(project(":libfdx:assets:manager"))
    api(project(":libfdx:assets:loaders"))
    implementation(project(":libfdx:runtime:fdx:core"))
    shaderToolClasspath(project(":libfdx:tools:shader:core"))
}
