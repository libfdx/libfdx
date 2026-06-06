import io.github.libfdx.build.LibExt

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.benchmark"


base {
    archivesName.set("benchmark_core")
}

dependencies {
    if (LibExt.usePublishedLibfdx) {
        api("${LibExt.fdxGroup}:application:${LibExt.publishedLibfdxVersion}")
        api("${LibExt.fdxGroup}:graphics_api:${LibExt.publishedLibfdxVersion}")
        api("${LibExt.fdxGroup}:g2d:${LibExt.publishedLibfdxVersion}")
    } else {
        api(project(":libfdx:runtime:application"))
        api(project(":libfdx:graphics:api"))
        api(project(":libfdx:graphics:g2d"))
    }
}
