import io.github.libfdx.build.LibExt

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.ui"

base {
    archivesName.set("ui_kit")
}

dependencies {
    api(project(":libfdx:foundation:core"))
    api(project(":libfdx:runtime:display"))
    api(project(":libfdx:runtime:files"))
    api(project(":libfdx:runtime:input"))
    api(project(":libfdx:assets:loaders"))
    api(project(":libfdx:graphics:api"))
    api(project(":libfdx:graphics:g2d"))
}
