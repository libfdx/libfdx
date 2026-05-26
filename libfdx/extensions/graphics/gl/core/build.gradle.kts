plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "io.github.libfdx.gl"

base {
    archivesName.set("gl_core")
}

dependencies {
    api(project(":libfdx:foundation:core"))
    api(project(":libfdx:graphics:api"))
}
