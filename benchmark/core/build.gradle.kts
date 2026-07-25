
plugins {
    id("java-library")
}


base {
    archivesName.set("benchmark_core")
}

dependencies {
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        api("${libs.versions.libfdxGroup.get()}:application:${libs.versions.libfdxSnapshot.get()}")
        api("${libs.versions.libfdxGroup.get()}:graphics:${libs.versions.libfdxSnapshot.get()}")
        api("${libs.versions.libfdxGroup.get()}:g2d:${libs.versions.libfdxSnapshot.get()}")
    } else {
        api(project(":libfdx:framework:application"))
        api(project(":libfdx:framework:graphics"))
        api(project(":libfdx:framework:g2d"))
    }
}
