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
    archivesName.set("webrtc_desktop_jni")
}

dependencies {
    api(project(":libfdx:extensions:net:webrtc:core"))
    api(libs.webrtc.java)
    implementation(libs.java.websocket)
    testImplementation(project(":libfdx:extensions:net:webrtc:signaling_server"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    runtimeOnly("dev.onvoid.webrtc:webrtc-java:${libs.versions.webrtc.java.get()}:windows-x86_64")
    runtimeOnly("dev.onvoid.webrtc:webrtc-java:${libs.versions.webrtc.java.get()}:linux-x86_64")
    runtimeOnly("dev.onvoid.webrtc:webrtc-java:${libs.versions.webrtc.java.get()}:macos-x86_64")
    runtimeOnly("dev.onvoid.webrtc:webrtc-java:${libs.versions.webrtc.java.get()}:macos-aarch64")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
