import io.github.libfdx.build.LibExt

plugins {
    id("java-library")
}

group = "${LibExt.fdxGroup}.benchmark"

base {
    archivesName.set("benchmark_core")
}

dependencies {
    if (LibExt.usePublishedLibfdx) {
        api("${LibExt.fdxGroup}:application:${LibExt.publishedLibfdxVersion}")
        api("${LibExt.fdxGroup}:graphics:${LibExt.publishedLibfdxVersion}")
        api("${LibExt.fdxGroup}:g2d:${LibExt.publishedLibfdxVersion}")
    } else {
        api(project(":libfdx:runtime:application"))
        api(project(":libfdx:graphics:api"))
        api(project(":libfdx:graphics:g2d"))
    }
}
