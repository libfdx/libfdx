
plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}


base {
    archivesName.set("sample_2d_sprite_movement_ios_c")
}

dependencies {
    implementation(project(":samples:2d:sprite-movement:core"))
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_ios_c:${libs.versions.libfdxSnapshot.get()}")
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
