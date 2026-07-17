plugins {
    id("maven-publish")
    id("java-library")
}

val java25 = JavaVersion.toVersion(25)

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    sourceCompatibility = java25
    targetCompatibility = java25
}

val moduleName = "wgpu_desktop_ffm"

base {
    archivesName.set(moduleName)
}

dependencies {
    api(project(":libfdx:extensions:graphics:wgpu:core"))
    runtimeOnly(libs.bundles.jwebgpu.desktop.ffm)
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
