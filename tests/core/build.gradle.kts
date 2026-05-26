plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "io.github.libfdx.tests"

base {
    archivesName.set("tests_core")
}

dependencies {
    api(project(":libfdx:runtime:application"))
    api(project(":libfdx:runtime:input"))
    api(project(":libfdx:graphics:api"))
    api(project(":libfdx:graphics:g2d"))
    api(project(":libfdx:graphics:g3d"))
    api(project(":libfdx:ui:ui-kit"))
    api(project(":libfdx:validation:scenario-validator"))
    api(project(":libfdx:validation:scenario-validator-ui-kit"))
}
