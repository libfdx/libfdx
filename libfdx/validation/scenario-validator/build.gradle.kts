import io.github.libfdx.build.LibExt

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = LibExt.fdxGroup

base {
    archivesName.set("scenario_validator")
}

dependencies {
    api(project(":libfdx:runtime:fdx:core"))
    api(project(":libfdx:runtime:display"))
    api(project(":libfdx:runtime:input"))
}
