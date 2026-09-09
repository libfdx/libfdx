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
base { archivesName.set("tools_texturepacker") }
dependencies {
    api(project(":libfdx:framework:assets:loaders"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
tasks.named<Test>("test") { useJUnitPlatform() }
publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "tools_texturepacker"
            from(components["java"])
        }
    }
}
