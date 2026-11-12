
plugins {
    id("java-library")
}

base {
    archivesName.set("benchmark_core")
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    api(project(":libfdx:framework:application"))
    api(project(":libfdx:framework:graphics"))
    api(project(":libfdx:framework:g2d"))
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
