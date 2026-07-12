import org.gradle.api.tasks.testing.Test

plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

val moduleName = "backend_psp"

base {
    archivesName.set(moduleName)
}

dependencies {
    implementation(project(":libfdx:backends:c_shared"))
    implementation(project(":libfdx:tools:font"))
    api(project(":libfdx:framework:application"))
    api(project(":libfdx:framework:graphics"))

    api(libs.teavm.interop)
    compileOnly("org.teavm:teavm-core:${libs.versions.teavm.get()}")

    runtimeOnly(project(":libfdx:framework:fdx:platform:shared"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
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
