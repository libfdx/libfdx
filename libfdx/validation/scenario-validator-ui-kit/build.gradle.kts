import io.github.libfdx.build.LibExt

plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = LibExt.fdxGroup

val moduleName = "scenario_validator_ui_kit"

base {
    archivesName.set(moduleName)
}

dependencies {
    api(project(":libfdx:validation:scenario-validator"))
    api(project(":libfdx:ui:ui-kit"))
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
