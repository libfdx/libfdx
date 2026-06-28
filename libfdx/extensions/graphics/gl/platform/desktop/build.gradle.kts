import io.github.libfdx.build.LibExt

plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.gl"

val moduleName = "gl_desktop"

base {
    archivesName.set(moduleName)
}

dependencies {
    api(libs.lwjgl.opengl)
    api(variantOf(libs.lwjgl.opengl) { classifier("natives-windows") })
    api(variantOf(libs.lwjgl.opengl) { classifier("natives-linux") })
    api(variantOf(libs.lwjgl.opengl) { classifier("natives-macos") })
    api(variantOf(libs.lwjgl.opengl) { classifier("natives-macos-arm64") })
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
