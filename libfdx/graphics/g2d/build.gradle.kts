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
    archivesName.set("g2d")
}

dependencies {
    api(project(":libfdx:graphics:api"))
    api(project(":libfdx:assets:manager"))
    api(project(":libfdx:assets:loaders"))
    implementation(project(":libfdx:foundation:collections"))
    implementation(project(":libfdx:runtime:fdx:core"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
