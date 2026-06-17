plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("shader_compiler_web")
}

dependencies {
    api(project(":libfdx:tools:shader:core"))
    implementation(libs.teavm.jso)
}

val generatedShadercResources = layout.buildDirectory.dir("generated/resources/shaderc")
val requiredWebShadercResources = listOf(
    "libfdx/shader/native/web/libfdx_shaderc.js",
    "libfdx/shader/native/web/libfdx_shaderc.wasm"
)

tasks.register<Sync>("generate_shaderc_web_native") {
    group = "libfdx native"
    description = "Builds and stages the web shader compiler JS/Wasm resources."
    dependsOn(":libfdx:tools:shader:core:build_shaderc_web")
    from(project(":libfdx:tools:shader:core").layout.buildDirectory.dir("native/shaderc/web")) {
        include("libfdx_shaderc.js", "libfdx_shaderc.wasm")
        into("libfdx/shader/native/web")
    }
    into(generatedShadercResources)
}

val validateShadercWebNativeResources = tasks.register("validate_shaderc_web_native_resources") {
    group = "libfdx native"
    description = "Validates staged web shader compiler JS/Wasm resources before packaging."
    mustRunAfter("generate_shaderc_web_native")
    inputs.files(requiredWebShadercResources.map { path ->
        generatedShadercResources.map { dir -> dir.file(path) }
    })
    doLast {
        val root = generatedShadercResources.get().asFile
        val missing = requiredWebShadercResources
            .map { root.resolve(it) }
            .filterNot { it.isFile }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing generated web shader compiler resources:\n" +
                        missing.joinToString(separator = "\n") { " - ${it.absolutePath}" }
            )
        }
    }
}

sourceSets {
    main {
        resources.srcDir(generatedShadercResources)
    }
}

tasks.named<ProcessResources>("processResources") {
    mustRunAfter("generate_shaderc_web_native")
}

tasks.named("jar") {
    dependsOn(validateShadercWebNativeResources)
}
