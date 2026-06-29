import io.github.libfdx.build.LibExt

import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.assets"

val moduleName = "asset_loaders"

base {
    archivesName.set(moduleName)
}

dependencies {
    api(project(":libfdx:framework:assets:manager"))
    api(project(":libfdx:framework:json"))
    compileOnly(libs.teavm.interop)
    compileOnly(libs.teavm.jso)
    compileOnly(libs.teavm.jso.apis)
    compileOnly("org.teavm:teavm-core:${libs.versions.teavm.get()}")
}

configurations.compileClasspath {
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
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
