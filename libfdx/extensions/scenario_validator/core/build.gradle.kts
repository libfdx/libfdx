
plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

val moduleName = "scenario_validator"


base {
    archivesName.set(moduleName)
}

dependencies {
    api(project(":libfdx:framework:collections"))
    api(project(":libfdx:framework:fdx:core"))
    api(project(":libfdx:framework:display"))
    api(project(":libfdx:framework:input"))

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
