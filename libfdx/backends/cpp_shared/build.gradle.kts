plugins {
    `java-library`
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
    withSourcesJar()
    withJavadocJar()
}

base {
    archivesName.set("backend_cpp_shared")
}

dependencies {
    api(libs.jnative.core)
    api(libs.jnative.interop)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "backend_cpp_shared"
            from(components["java"])
        }
    }
}
