plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "io.github.libfdx.vulkan"

base {
    archivesName.set("vulkan_core")
}

dependencies {
    api(project(":libfdx:foundation:core"))
    api(project(":libfdx:graphics:api"))
}
