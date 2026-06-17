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

sourceSets {
    main {
        resources.srcDir(generatedShadercResources)
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn("generate_shaderc_web_native")
}
