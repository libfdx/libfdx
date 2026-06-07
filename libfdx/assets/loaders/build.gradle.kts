import io.github.libfdx.build.LibExt

import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.assets"

base {
    archivesName.set("asset_loaders")
}

dependencies {
    api(project(":libfdx:assets:manager"))
    api(project(":libfdx:foundation:json"))
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
