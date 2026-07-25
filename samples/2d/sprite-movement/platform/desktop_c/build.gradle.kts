
plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}


base {
    archivesName.set("sample_2d_sprite_movement_desktop_c")
}

dependencies {
    implementation(project(":samples:2d:sprite-movement:core"))
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_desktop_c:${libs.versions.libfdxSnapshot.get()}")
        runtimeOnly("${libs.versions.libfdxGroup.get()}:gl_desktop_c:${libs.versions.libfdxSnapshot.get()}")
    } else {
        implementation(project(":libfdx:backends:desktop_c"))
        runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop_c"))
    }
}

tasks.register("sprite_movement_desktop_c_opengl_generate_debug") {
    group = "application"
    description = "Generates the 2D Sprite Movement desktop_c OpenGL Debug project."
    dependsOn(":samples:2d:sprite-movement:platform:plugin:libfdx_desktop_c_opengl_generate_debug")
}

tasks.register("sprite_movement_desktop_c_opengl_generate_release") {
    group = "application"
    description = "Generates the 2D Sprite Movement desktop_c OpenGL Release project."
    dependsOn(":samples:2d:sprite-movement:platform:plugin:libfdx_desktop_c_opengl_generate_release")
}

tasks.register("sprite_movement_desktop_c_opengl_build_debug") {
    group = "application"
    description = "Builds the 2D Sprite Movement desktop_c OpenGL Debug executable."
    dependsOn(":samples:2d:sprite-movement:platform:plugin:libfdx_desktop_c_opengl_build_debug")
}

tasks.register("sprite_movement_desktop_c_opengl_build_release") {
    group = "application"
    description = "Builds the 2D Sprite Movement desktop_c OpenGL Release executable."
    dependsOn(":samples:2d:sprite-movement:platform:plugin:libfdx_desktop_c_opengl_build_release")
}

tasks.register("sprite_movement_desktop_c_opengl_run_debug") {
    group = "application"
    description = "Runs the 2D Sprite Movement desktop_c OpenGL Debug executable."
    dependsOn(":samples:2d:sprite-movement:platform:plugin:libfdx_desktop_c_opengl_run_debug")
}

tasks.register("sprite_movement_desktop_c_opengl_run_release") {
    group = "application"
    description = "Runs the 2D Sprite Movement desktop_c OpenGL Release executable."
    dependsOn(":samples:2d:sprite-movement:platform:plugin:libfdx_desktop_c_opengl_run_release")
}
