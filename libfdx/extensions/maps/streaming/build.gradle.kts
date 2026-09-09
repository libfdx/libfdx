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
base { archivesName.set("map_streaming") }
dependencies {
    api(project(":libfdx:framework:maps"))
    api(project(":libfdx:framework:assets:manager"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
tasks.named<Test>("test") { useJUnitPlatform() }
publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "map_streaming"
            from(components["java"])
        }
    }
}

