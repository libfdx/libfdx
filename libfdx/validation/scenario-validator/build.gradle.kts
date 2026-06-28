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

val moduleName = "scenario_validator"

base {
    archivesName.set(moduleName)
}

dependencies {
    api(project(":libfdx:runtime:fdx:core"))
    api(project(":libfdx:runtime:display"))
    api(project(":libfdx:runtime:input"))
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
