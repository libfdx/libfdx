import io.github.libfdx.build.LibExt

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.files"

base {
    archivesName.set("files")
}

dependencies {
    api(project(":libfdx:runtime:fdx:core"))
}
