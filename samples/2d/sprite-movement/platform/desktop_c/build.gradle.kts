
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

val sampleProjectPath = project.path.substringBefore(":platform:")
val libfdxDependencyVersion =
    gradle.extensions.extraProperties.get("libfdxDependencyVersion") as String

dependencies {
    implementation(project("$sampleProjectPath:core"))
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_desktop_c:$libfdxDependencyVersion")
        runtimeOnly("${libs.versions.libfdxGroup.get()}:gl_desktop_c:$libfdxDependencyVersion")
    } else {
        implementation(project(":libfdx:backends:desktop_c"))
        runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop_c"))
    }
}

tasks.register("sprite_movement_desktop_c_opengl_generate_debug") {
    group = "application"
    description = "Generates the 2D Sprite Movement desktop_c OpenGL Debug project."
    dependsOn("$sampleProjectPath:platform:plugin:libfdx_desktop_c_opengl_generate_debug")
}

tasks.register("sprite_movement_desktop_c_opengl_generate_release") {
    group = "application"
    description = "Generates the 2D Sprite Movement desktop_c OpenGL Release project."
    dependsOn("$sampleProjectPath:platform:plugin:libfdx_desktop_c_opengl_generate_release")
}

tasks.register("sprite_movement_desktop_c_opengl_build_debug") {
    group = "application"
    description = "Builds the 2D Sprite Movement desktop_c OpenGL Debug executable."
    dependsOn("$sampleProjectPath:platform:plugin:libfdx_desktop_c_opengl_build_debug")
}

tasks.register("sprite_movement_desktop_c_opengl_build_release") {
    group = "application"
    description = "Builds the 2D Sprite Movement desktop_c OpenGL Release executable."
    dependsOn("$sampleProjectPath:platform:plugin:libfdx_desktop_c_opengl_build_release")
}

tasks.register("sprite_movement_desktop_c_opengl_run_debug") {
    group = "application"
    description = "Runs the 2D Sprite Movement desktop_c OpenGL Debug executable."
    dependsOn("$sampleProjectPath:platform:plugin:libfdx_desktop_c_opengl_run_debug")
}

tasks.register("sprite_movement_desktop_c_opengl_run_release") {
    group = "application"
    description = "Runs the 2D Sprite Movement desktop_c OpenGL Release executable."
    dependsOn("$sampleProjectPath:platform:plugin:libfdx_desktop_c_opengl_run_release")
}
