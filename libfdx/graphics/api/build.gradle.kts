plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("graphics")
}

dependencies {
    api(project(":libfdx:runtime:fdx:core"))
    api(project(":libfdx:foundation:math"))
    api(project(":libfdx:runtime:display"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
