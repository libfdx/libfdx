import io.github.libfdx.build.LibExt

plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.samples.basic"

base {
    archivesName.set("sample_basic_ios_c")
}

dependencies {
    implementation(project(":samples:basic:core"))
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_ios_c:${LibExt.publishedLibfdxVersion}")
    } else {
        implementation(project(":libfdx:backends:ios_c"))
    }
}

tasks.register("basic_ios_c_gles_generate") {
    group = "application"
    description = "Generates the basic iOS C GLES TeaVM and Xcode project."
    dependsOn(":samples:basic:platform:plugin:libfdx_ios_c_gles_generate")
}

tasks.register("basic_ios_c_metal_generate") {
    group = "application"
    description = "Generates the basic iOS C Metal TeaVM and Xcode project."
    dependsOn(":samples:basic:platform:plugin:libfdx_ios_c_metal_generate")
}
