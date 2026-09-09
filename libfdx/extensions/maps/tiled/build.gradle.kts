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
base { archivesName.set("tiled") }
dependencies {
    api(project(":libfdx:framework:maps"))
    api(project(":libfdx:framework:assets:manager"))
    api(project(":libfdx:framework:json"))
    testImplementation(libs.junit.jupiter)
    testImplementation(project(":libfdx:framework:g2d"))
    testRuntimeOnly(libs.junit.platform.launcher)
}
tasks.named<Test>("test") { useJUnitPlatform() }
sourceSets.named("test") {
    resources.srcDir(rootProject.layout.projectDirectory.dir("tests/assets"))
    resources.include("tiled/**")
}
publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "tiled"
            from(components["java"])
        }
    }
}
