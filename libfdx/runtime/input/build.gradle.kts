plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "io.github.libfdx.input"

base {
    archivesName.set("input")
}

dependencies {
    api(project(":libfdx:foundation:core"))
}
