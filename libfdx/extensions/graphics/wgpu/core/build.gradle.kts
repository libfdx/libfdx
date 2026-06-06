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
    archivesName.set("wgpu_core")
}

dependencies {
    api(project(":libfdx:runtime:core"))
    api(project(":libfdx:graphics:api"))
    compileOnlyApi(libs.jwebgpu.core)
    compileOnly(libs.jwebgpu.jni)
}
