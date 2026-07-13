import io.github.libfdx.build.LibExt

plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.samples.basic"

base {
    archivesName.set("sample_basic_desktop_c")
}

dependencies {
    implementation(project(":samples:basic:core"))
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_desktop_c:${LibExt.fdxSnapshotVersion}")
        runtimeOnly("${LibExt.fdxGroup}:gl_desktop_c:${LibExt.fdxSnapshotVersion}")
    } else {
        implementation(project(":libfdx:backends:desktop_c"))
        runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop_c"))
    }
}

tasks.register("basic_desktop_c_opengl_generate_debug") {
    group = "application"
    description = "Generates the basic desktop_c OpenGL Debug project."
    dependsOn(":samples:basic:platform:plugin:libfdx_desktop_c_opengl_generate_debug")
}

tasks.register("basic_desktop_c_opengl_generate_release") {
    group = "application"
    description = "Generates the basic desktop_c OpenGL Release project."
    dependsOn(":samples:basic:platform:plugin:libfdx_desktop_c_opengl_generate_release")
}

tasks.register("basic_desktop_c_opengl_build_debug") {
    group = "application"
    description = "Builds the basic desktop_c OpenGL Debug executable."
    dependsOn(":samples:basic:platform:plugin:libfdx_desktop_c_opengl_build_debug")
}

tasks.register("basic_desktop_c_opengl_build_release") {
    group = "application"
    description = "Builds the basic desktop_c OpenGL Release executable."
    dependsOn(":samples:basic:platform:plugin:libfdx_desktop_c_opengl_build_release")
}

tasks.register("basic_desktop_c_opengl_run_debug") {
    group = "application"
    description = "Runs the basic desktop_c OpenGL Debug executable."
    dependsOn(":samples:basic:platform:plugin:libfdx_desktop_c_opengl_run_debug")
}

tasks.register("basic_desktop_c_opengl_run_release") {
    group = "application"
    description = "Runs the basic desktop_c OpenGL Release executable."
    dependsOn(":samples:basic:platform:plugin:libfdx_desktop_c_opengl_run_release")
}
