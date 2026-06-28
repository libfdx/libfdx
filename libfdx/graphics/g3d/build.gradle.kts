import io.github.libfdx.build.LibExt

plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.graphics"

val moduleName = "g3d"

base {
    archivesName.set(moduleName)
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
