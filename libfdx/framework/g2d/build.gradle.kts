
plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}


val moduleName = "g2d"

base {
    archivesName.set(moduleName)
}

dependencies {
    api(project(":libfdx:framework:graphics"))
    api(project(":libfdx:framework:assets:manager"))
    api(project(":libfdx:framework:assets:loaders"))
    api(project(":libfdx:framework:collections"))
    implementation(project(":libfdx:framework:fdx:core"))
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
