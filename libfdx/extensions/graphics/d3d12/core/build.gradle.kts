
plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
    withSourcesJar()
    withJavadocJar()
}


val moduleName = "d3d12_core"

base {
    archivesName.set(moduleName)
}

dependencies {
    api(project(":libfdx:framework:fdx:core"))
    api(project(":libfdx:framework:graphics"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = moduleName
            from(components["java"])
        }
    }
}
