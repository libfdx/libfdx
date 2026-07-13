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
        api("${LibExt.fdxGroup}:application:${LibExt.fdxSnapshotVersion}")
        api("${LibExt.fdxGroup}:graphics:${LibExt.fdxSnapshotVersion}")
        api("${LibExt.fdxGroup}:g2d:${LibExt.fdxSnapshotVersion}")
    } else {
        api(project(":libfdx:framework:application"))
        api(project(":libfdx:framework:graphics"))
        api(project(":libfdx:framework:g2d"))
    }
}
