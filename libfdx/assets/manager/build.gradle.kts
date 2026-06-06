import io.github.libfdx.build.LibExt

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.assets"

base {
    archivesName.set("asset_manager")
}

dependencies {
    api(project(":libfdx:runtime:core"))
    api(project(":libfdx:runtime:files"))
}
