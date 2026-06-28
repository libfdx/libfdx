import io.github.libfdx.build.LibExt

plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.webrtc"

val moduleName = "webrtc_web"

base {
    archivesName.set(moduleName)
}

dependencies {
    api(project(":libfdx:extensions:net:webrtc:core"))
    compileOnly(libs.teavm.jso)
    compileOnly(libs.teavm.jso.apis)
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = moduleName
            from(components["java"])
        }
    }
}
