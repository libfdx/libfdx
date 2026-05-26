plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "io.github.libfdx"

base {
    archivesName.set("scenario_validator")
}

dependencies {
    api(project(":libfdx:foundation:core"))
    api(project(":libfdx:runtime:display"))
    api(project(":libfdx:runtime:input"))
}
