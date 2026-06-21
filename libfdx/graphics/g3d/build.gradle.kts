import io.github.libfdx.build.LibExt

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.graphics"

base {
    archivesName.set("g3d")
}

dependencies {
    api(project(":libfdx:graphics:api"))
    api(project(":libfdx:graphics:camera"))
    api(project(":libfdx:foundation:math"))
    implementation(project(":libfdx:foundation:json"))
    api(project(":libfdx:assets:manager"))
    api(project(":libfdx:assets:loaders"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
