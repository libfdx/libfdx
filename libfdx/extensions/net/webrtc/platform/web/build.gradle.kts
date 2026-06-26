import io.github.libfdx.build.LibExt

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.webrtc"

base {
    archivesName.set("webrtc_web")
}

dependencies {
    api(project(":libfdx:extensions:net:webrtc:core"))
    compileOnly(libs.teavm.jso)
    compileOnly(libs.teavm.jso.apis)
}
