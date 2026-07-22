import io.github.libfdx.build.LibExt

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.samples.ecs.platformer"

base {
    archivesName.set("sample_ecs_platformer_core")
}

dependencies {
    if (LibExt.usePublishedLibfdx) {
        api("${LibExt.fdxGroup}:application:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:graphics:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:g2d:${LibExt.fdxSnapshotVersion}")
        implementation("${LibExt.fdxGroup}:ecs:${LibExt.fdxSnapshotVersion}")
    } else {
        api(project(":libfdx:framework:application"))
        implementation(project(":libfdx:framework:graphics"))
        implementation(project(":libfdx:framework:g2d"))
        implementation(project(":libfdx:extensions:ecs:core"))
    }
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
