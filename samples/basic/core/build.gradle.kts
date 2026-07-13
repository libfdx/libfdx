import io.github.libfdx.build.LibExt

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.samples.basic"


base {
    archivesName.set("sample_basic_core")
}

dependencies {
    if (LibExt.usePublishedLibfdx) {
        api("${LibExt.fdxGroup}:application:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:graphics:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:g2d:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:ui_kit:${LibExt.fdxSnapshotVersion}")
    } else {
        api(project(":libfdx:framework:application"))
        implementation(project(":libfdx:framework:graphics"))
        implementation(project(":libfdx:framework:g2d"))
        implementation(project(":libfdx:framework:ui-kit"))
    }
}
