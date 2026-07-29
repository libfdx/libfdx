plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

val moduleName = "box3d_ext"

base {
    archivesName.set(moduleName)
}

dependencies {
    compileOnlyApi(libs.jbox3d.core)
    api(project(":libfdx:framework:graphics"))
    api(project(":libfdx:framework:g3d"))
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
