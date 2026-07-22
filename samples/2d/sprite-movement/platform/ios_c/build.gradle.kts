import io.github.libfdx.build.LibExt

plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.samples.g2d.spritemovement"

base {
    archivesName.set("sample_2d_sprite_movement_ios_c")
}

dependencies {
    implementation(project(":samples:2d:sprite-movement:core"))
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_ios_c:${LibExt.fdxSnapshotVersion}")
    } else {
        implementation(project(":libfdx:backends:ios_c"))
    }
}

tasks.register("sprite_movement_ios_c_gles_generate") {
    group = "application"
    description = "Generates the 2D Sprite Movement iOS C GLES TeaVM and Xcode project."
    dependsOn(":samples:2d:sprite-movement:platform:plugin:libfdx_ios_c_gles_generate")
}

tasks.register("sprite_movement_ios_c_metal_generate") {
    group = "application"
    description = "Generates the 2D Sprite Movement iOS C Metal TeaVM and Xcode project."
    dependsOn(":samples:2d:sprite-movement:platform:plugin:libfdx_ios_c_metal_generate")
}
