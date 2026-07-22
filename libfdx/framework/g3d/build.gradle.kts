
plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}


val moduleName = "g3d"

base {
    archivesName.set(moduleName)
}

dependencies {
    api(project(":libfdx:framework:graphics"))
    api(project(":libfdx:framework:camera"))
    api(project(":libfdx:framework:math"))
    implementation(project(":libfdx:framework:json"))
    api(project(":libfdx:framework:assets:manager"))
    api(project(":libfdx:framework:assets:loaders"))
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
