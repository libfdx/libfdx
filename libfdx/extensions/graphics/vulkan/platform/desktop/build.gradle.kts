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
    archivesName.set("vulkan_desktop")
}

dependencies {
    api(project(":libfdx:extensions:graphics:vulkan:core"))
    runtimeOnly(libs.lwjgl.vulkan)
}
