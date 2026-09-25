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
base { archivesName.set("meshoptimizer_core") }
dependencies {
    api(project(":libfdx:framework:g3d"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
tasks.named<Test>("test") { useJUnitPlatform() }
publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "meshoptimizer_core"
            from(components["java"])
        }
    }
}
