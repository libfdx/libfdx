plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "io.github.libfdx.graphics"

base {
    archivesName.set("g2d")
}

dependencies {
    api(project(":libfdx:graphics:api"))
    api(project(":libfdx:assets:manager"))
    api(project(":libfdx:assets:loaders"))
    implementation(project(":libfdx:runtime:core"))
}
