plugins { id("java-library"); id("maven-publish") }
java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
    withSourcesJar(); withJavadocJar()
}
base { archivesName.set("audio") }
dependencies {
    api(project(":libfdx:framework:fdx:core"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
tasks.named<Test>("test") { useJUnitPlatform() }
publishing { publications { create<MavenPublication>("maven") {
    artifactId = "audio"; from(components["java"])
} } }
