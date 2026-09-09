plugins { id("java-library"); id("maven-publish") }
java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
    withSourcesJar(); withJavadocJar()
}
base { archivesName.set("audio_openal") }
dependencies {
    api(project(":libfdx:framework:audio"))
    implementation(libs.lwjgl)
    implementation(libs.lwjgl.openal)
    for (platform in listOf("windows", "linux", "macos", "macos-arm64")) {
        runtimeOnly("org.lwjgl:lwjgl:${libs.versions.lwjgl.get()}:natives-$platform")
        runtimeOnly("org.lwjgl:lwjgl-openal:${libs.versions.lwjgl.get()}:natives-$platform")
    }
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
tasks.named<Test>("test") { useJUnitPlatform() }
publishing { publications { create<MavenPublication>("maven") {
    artifactId = "audio_openal"; from(components["java"])
} } }
