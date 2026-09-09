plugins { id("java-library"); id("maven-publish") }
java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
    withSourcesJar(); withJavadocJar()
}
base { archivesName.set("audio_loaders") }
dependencies {
    api(project(":libfdx:framework:audio"))
    api(project(":libfdx:framework:assets:manager"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
tasks.named<Test>("test") { useJUnitPlatform() }
publishing { publications { create<MavenPublication>("maven") {
    artifactId = "audio_loaders"; from(components["java"])
} } }
