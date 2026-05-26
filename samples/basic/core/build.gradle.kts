plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "io.github.libfdx.samples.basic"

base {
    archivesName.set("sample_basic_core")
}

dependencies {
    api(project(":libfdx:runtime:application"))
    implementation(project(":libfdx:graphics:api"))
    implementation(project(":libfdx:graphics:g2d"))
    implementation(project(":libfdx:ui:ui-kit"))
}
