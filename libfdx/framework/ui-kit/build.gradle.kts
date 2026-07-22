
plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}


val moduleName = "ui_kit"

base {
    archivesName.set(moduleName)
}

dependencies {
    api(project(":libfdx:framework:fdx:core"))
    api(project(":libfdx:framework:display"))
    api(project(":libfdx:framework:files"))
    api(project(":libfdx:framework:input"))
    api(project(":libfdx:framework:assets:loaders"))
    api(project(":libfdx:framework:graphics"))
    api(project(":libfdx:framework:g2d"))
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
