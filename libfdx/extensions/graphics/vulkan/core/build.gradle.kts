import io.github.libfdx.build.LibExt

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.vulkan"

base {
    archivesName.set("vulkan_core")
}

dependencies {
    api(project(":libfdx:runtime:fdx:core"))
    api(project(":libfdx:graphics:api"))
}
