import io.github.libfdx.build.LibExt

plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.wgpu"

val moduleName = "wgpu_core"

base {
    archivesName.set(moduleName)
}

dependencies {
    api(project(":libfdx:runtime:fdx:core"))
    api(project(":libfdx:graphics:api"))
    compileOnlyApi(libs.jwebgpu.core)
    compileOnly(libs.jwebgpu.jni)
}
java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = moduleName
            from(components["java"])
        }
    }
}
