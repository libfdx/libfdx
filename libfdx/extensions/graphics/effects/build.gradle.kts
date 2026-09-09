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
base { archivesName.set("graphics_effects") }
dependencies {
    api(project(":libfdx:framework:graphics"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
tasks.named<Test>("test") { useJUnitPlatform() }
publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "graphics_effects"
            from(components["java"])
        }
    }
}
