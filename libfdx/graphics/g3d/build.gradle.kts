plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "io.github.libfdx.graphics"

base {
    archivesName.set("g3d")
}

dependencies {
    api(project(":libfdx:graphics:api"))
    api(project(":libfdx:foundation:math"))
    api(project(":libfdx:assets:manager"))
    api(project(":libfdx:assets:loaders"))
}
