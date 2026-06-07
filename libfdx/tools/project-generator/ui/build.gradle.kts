import io.github.libfdx.build.LibExt

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("project_generator_ui")
}

group = "${LibExt.fdxGroup}.tools.projectgenerator"

dependencies {
    api(project(":libfdx:tools:project-generator:core"))
    api(project(":libfdx:runtime:application"))
    implementation(project(":libfdx:ui:ui-kit"))
}
