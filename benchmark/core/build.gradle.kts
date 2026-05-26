plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "io.github.libfdx.benchmark"

base {
    archivesName.set("benchmark_core")
}

dependencies {
    api(project(":libfdx:runtime:application"))
    api(project(":libfdx:graphics:api"))
    api(project(":libfdx:graphics:g2d"))
}
