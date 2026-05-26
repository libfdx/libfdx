plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "io.github.libfdx"

base {
    archivesName.set("scenario_validator_ui_kit")
}

dependencies {
    api(project(":libfdx:validation:scenario-validator"))
    api(project(":libfdx:ui:ui-kit"))
}
