import io.github.libfdx.build.LibExt
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.webrtc"

val moduleName = "webrtc_signaling_server"

base {
    archivesName.set(moduleName)
}

dependencies {
    api(project(":libfdx:extensions:net:webrtc:core"))
    api(libs.java.websocket)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
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

val signalingMainClass = "io.github.libfdx.net.webrtc.signaling.server.WebRtcSignalingServerLauncher"

tasks.register<JavaExec>("webrtc_signaling_server_run") {
    group = "application"
    description = "Runs the standalone libFDX WebRTC signaling server."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(signalingMainClass)
    workingDir = rootProject.projectDir
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    System.getProperty("libfdx.webrtc.signaling.host")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("libfdx.webrtc.signaling.host", it)
    }
    System.getProperty("libfdx.webrtc.signaling.port")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("libfdx.webrtc.signaling.port", it)
    }
    System.getProperty("libfdx.webrtc.signaling.maxPeersPerRoom")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("libfdx.webrtc.signaling.maxPeersPerRoom", it)
    }
    System.getProperty("libfdx.webrtc.signaling.idleTimeoutMillis")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("libfdx.webrtc.signaling.idleTimeoutMillis", it)
    }
    System.getProperty("libfdx.webrtc.signaling.log")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("libfdx.webrtc.signaling.log", it)
    }
    System.getProperty("libfdx.webrtc.signaling.tickRate")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("libfdx.webrtc.signaling.tickRate", it)
    }
    System.getProperty("libfdx.webrtc.signaling.maxTicksPerFrame")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("libfdx.webrtc.signaling.maxTicksPerFrame", it)
    }
    System.getProperty("libfdx.webrtc.signaling.maxEventsPerTick")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("libfdx.webrtc.signaling.maxEventsPerTick", it)
    }
    System.getProperty("libfdx.webrtc.signaling.maxBytesPerTick")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("libfdx.webrtc.signaling.maxBytesPerTick", it)
    }
    System.getProperty("libfdx.webrtc.signaling.initialEvents")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("libfdx.webrtc.signaling.initialEvents", it)
    }
    System.getProperty("libfdx.webrtc.signaling.maxQueuedEvents")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("libfdx.webrtc.signaling.maxQueuedEvents", it)
    }
}
