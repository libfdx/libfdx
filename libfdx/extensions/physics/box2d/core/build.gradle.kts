plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

val moduleName = "box2d_ext"

base {
    archivesName.set(moduleName)
}

dependencies {
    compileOnlyApi(libs.jbox2d.core)
    api(project(":libfdx:framework:graphics"))
    api(project(":libfdx:framework:camera"))
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
