
plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

val moduleName = "scenario_validator_ui_kit"


base {
    archivesName.set(moduleName)
}

dependencies {
    api(project(":libfdx:extensions:scenario_validator:core"))
    api(project(":libfdx:framework:ui-kit"))
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
