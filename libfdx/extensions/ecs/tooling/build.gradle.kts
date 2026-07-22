
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


val moduleName = "ecs_tooling"

base {
    archivesName.set(moduleName)
}

dependencies {
    api(project(":libfdx:extensions:ecs:core"))
    api(project(":libfdx:framework:application"))
    api(project(":libfdx:framework:graphics"))
    api(project(":libfdx:framework:camera"))
    api(project(":libfdx:framework:json"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
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
