
plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}


val moduleName = "gl_desktop_c"

base {
    archivesName.set(moduleName)
}

dependencies {
    api(project(":libfdx:extensions:graphics:gl:core"))
    runtimeOnly(project(":libfdx:backends:c_shared"))
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
