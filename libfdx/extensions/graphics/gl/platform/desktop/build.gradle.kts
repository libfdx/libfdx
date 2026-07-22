
plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}


val moduleName = "gl_desktop"
val lwjglVersion = libs.versions.lwjgl.get()
val lwjglNativeClassifiers = arrayOf(
    "natives-windows",
    "natives-linux",
    "natives-macos",
    "natives-macos-arm64"
)

base {
    archivesName.set(moduleName)
}

dependencies {
    api(libs.lwjgl.opengl)
    lwjglNativeClassifiers.forEach { classifier ->
        api("org.lwjgl:lwjgl-opengl:$lwjglVersion:$classifier")
    }
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
