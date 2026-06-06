import io.github.libfdx.build.LibExt

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.input"

base {
    archivesName.set("input")
}

dependencies {
    api(project(":libfdx:runtime:core"))
}
