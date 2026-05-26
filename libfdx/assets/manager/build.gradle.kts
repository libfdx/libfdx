plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "io.github.libfdx.assets"

base {
    archivesName.set("asset_manager")
}

dependencies {
    api(project(":libfdx:foundation:core"))
    api(project(":libfdx:runtime:files"))
}
