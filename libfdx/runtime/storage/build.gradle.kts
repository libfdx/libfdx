import io.github.libfdx.build.LibExt

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.storage"

base {
    archivesName.set("storage")
}

dependencies {
    api(project(":libfdx:runtime:fdx:core"))
    api(project(":libfdx:runtime:files"))
    api(project(":libfdx:foundation:json"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
