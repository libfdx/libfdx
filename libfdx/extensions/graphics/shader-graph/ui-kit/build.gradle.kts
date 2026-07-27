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

base {
    archivesName.set("shader_graph_ui_kit")
}

dependencies {
    api(project(":libfdx:extensions:graphics:shader-graph:core"))
    api(project(":libfdx:framework:ui-kit"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "shader_graph_ui_kit"
            from(components["java"])
        }
    }
}
