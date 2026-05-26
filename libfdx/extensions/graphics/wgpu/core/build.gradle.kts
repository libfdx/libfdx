plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "io.github.libfdx.wgpu"

base {
    archivesName.set("wgpu_core")
}

dependencies {
    api(project(":libfdx:foundation:core"))
    api(project(":libfdx:graphics:api"))
    compileOnlyApi(libs.jwebgpu.core)
    compileOnly(libs.jwebgpu.jni)
}
