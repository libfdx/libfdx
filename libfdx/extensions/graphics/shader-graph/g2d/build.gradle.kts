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
    archivesName.set("shader_graph_g2d")
}

dependencies {
    api(project(":libfdx:extensions:graphics:shader-graph:runtime"))
    api(project(":libfdx:framework:g2d"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "shader_graph_g2d"
            from(components["java"])
        }
    }
}
