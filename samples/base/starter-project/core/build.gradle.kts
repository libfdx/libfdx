plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("sample_base_starter_project_core")
}

val libfdxDependencyVersion =
    gradle.extensions.extraProperties.get("libfdxDependencyVersion") as String

dependencies {
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        api("${libs.versions.libfdxGroup.get()}:application:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:g2d:$libfdxDependencyVersion")
    } else {
        api(project(":libfdx:framework:application"))
        implementation(project(":libfdx:framework:g2d"))
    }
}

val sampleRoot = layout.projectDirectory.dir("..")

tasks.processResources {
    from(sampleRoot.dir("assets"))
}
