plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

val moduleName = "wgpu_desktop_jni"

base {
    archivesName.set(moduleName)
}

dependencies {
    api(project(":libfdx:extensions:graphics:wgpu:core"))
    runtimeOnly(libs.bundles.jwebgpu.desktop.jni)
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
