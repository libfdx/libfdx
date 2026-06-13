import io.github.libfdx.build.LibExt

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.gl"

base {
    archivesName.set("gl_desktop_c")
}

dependencies {
    api(project(":libfdx:extensions:graphics:gl:core"))
    runtimeOnly(project(":libfdx:backends:c_shared"))
}
