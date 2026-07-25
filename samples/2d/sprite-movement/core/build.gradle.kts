
plugins {
    id("java-library")
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}



base {
    archivesName.set("sample_2d_sprite_movement_core")
}

dependencies {
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        api("${libs.versions.libfdxGroup.get()}:application:${libs.versions.libfdxSnapshot.get()}")
        api("${libs.versions.libfdxGroup.get()}:ecs:${libs.versions.libfdxSnapshot.get()}")
        api("${libs.versions.libfdxGroup.get()}:ecs_tooling:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:asset_loaders:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:camera:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:collections:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:files:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:graphics:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:g2d:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:input:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:json:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:math:${libs.versions.libfdxSnapshot.get()}")
    } else {
        api(project(":libfdx:framework:application"))
        api(project(":libfdx:extensions:ecs:core"))
        api(project(":libfdx:extensions:ecs:tooling"))
        implementation(project(":libfdx:framework:assets:loaders"))
        implementation(project(":libfdx:framework:camera"))
        implementation(project(":libfdx:framework:collections"))
        implementation(project(":libfdx:framework:files"))
        implementation(project(":libfdx:framework:graphics"))
        implementation(project(":libfdx:framework:g2d"))
        implementation(project(":libfdx:framework:input"))
        implementation(project(":libfdx:framework:json"))
        implementation(project(":libfdx:framework:math"))
    }
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val sampleRoot = layout.projectDirectory.dir("..")

libfdx {
    ecsProject {
        projectId.set("io.github.libfdx.samples.g2d.spritemovement")
        entryClass.set("io.github.libfdx.samples.g2d.spritemovement.SpriteMovementProject")
        projectRoot.set(sampleRoot)
        gradleRoot.set("../../..")
        gradleProject.set(":samples:2d:sprite-movement:core")
        libfdxAbi.set(libs.versions.libfdxRelease.get())
    }
}

tasks.processResources {
    from(sampleRoot.dir("assets"))
    from(sampleRoot.dir("scenes")) {
        into("scenes")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    workingDir = sampleRoot.asFile
}
