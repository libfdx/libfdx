import io.github.libfdx.build.LibExt

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.wgpu"

base {
    archivesName.set("wgpu_web")
}

dependencies {
    api(project(":libfdx:extensions:graphics:wgpu:core"))

    implementation(libs.jwebgpu.web)
    implementation(libs.jwebgpu.web.wasm)
    implementation(libs.jmultiplatform)
    implementation(libs.teavm.jso)
    implementation(libs.teavm.jso.apis)
    implementation(libs.teavm.jso.impl)
}
