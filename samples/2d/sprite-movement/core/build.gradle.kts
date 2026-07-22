import io.github.libfdx.build.LibExt

plugins {
    id("java-library")
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.samples.g2d.spritemovement"


base {
    archivesName.set("sample_2d_sprite_movement_core")
}

dependencies {
    if (LibExt.usePublishedLibfdx) {
        api("${LibExt.fdxGroup}:application:${LibExt.fdxSnapshotVersion}")
        api("${LibExt.fdxGroup}:ecs:${LibExt.fdxSnapshotVersion}")
        api("${LibExt.fdxGroup}:ecs_tooling:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:asset_loaders:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:camera:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:collections:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:files:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:graphics:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:g2d:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:input:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:json:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:math:${LibExt.fdxSnapshotVersion}")
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
        libfdxAbi.set(LibExt.fdxVersion)
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
