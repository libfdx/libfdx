plugins { id("java-library"); id("maven-publish") }
java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
    withSourcesJar(); withJavadocJar()
}
base { archivesName.set("audio_web") }
dependencies {
    api(project(":libfdx:framework:audio"))
    implementation(libs.teavm.jso)
    implementation(libs.teavm.jso.apis)
}
publishing { publications { create<MavenPublication>("maven") {
    artifactId = "audio_web"; from(components["java"])
} } }
