plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "io.github.libfdx.files"

base {
    archivesName.set("files")
}

dependencies {
    api(project(":libfdx:foundation:core"))
}
