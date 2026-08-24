import org.gradle.api.tasks.testing.Test

plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

val moduleName = "backend_desktop"
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
    api(project(":libfdx:framework:fdx:core"))
    implementation(project(":libfdx:framework:math"))
    api(project(":libfdx:framework:assets:manager"))
    api(project(":libfdx:framework:application"))
    api(project(":libfdx:framework:display"))
    api(project(":libfdx:framework:files"))
    api(project(":libfdx:framework:input"))
    api(project(":libfdx:framework:graphics"))
    api(project(":libfdx:extensions:graphics:gl:core"))
    api(project(":libfdx:extensions:graphics:vulkan:core"))

    runtimeOnly(project(":libfdx:framework:fdx:platform:desktop"))

    api(libs.lwjgl)
    api(libs.lwjgl.freetype)
    api(libs.lwjgl.glfw)
    compileOnly(libs.lwjgl.opengl)
    compileOnly(libs.lwjgl.vulkan)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    lwjglNativeClassifiers.forEach { classifier ->
        api("org.lwjgl:lwjgl:$lwjglVersion:$classifier")
        api("org.lwjgl:lwjgl-freetype:$lwjglVersion:$classifier")
        api("org.lwjgl:lwjgl-glfw:$lwjglVersion:$classifier")
    }
}

tasks.withType<Test>().configureEach {
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
