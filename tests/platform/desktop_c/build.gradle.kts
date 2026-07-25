
plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}


base {
    archivesName.set("tests_desktop_c")
}

dependencies {
    implementation(project(":tests:core"))
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_desktop_c:${libs.versions.libfdxSnapshot.get()}")
        runtimeOnly("${libs.versions.libfdxGroup.get()}:gl_desktop_c:${libs.versions.libfdxSnapshot.get()}")
        runtimeOnly("${libs.versions.libfdxGroup.get()}:vulkan_desktop_c:${libs.versions.libfdxSnapshot.get()}")
    } else {
        implementation(project(":libfdx:backends:desktop_c"))
        runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop_c"))
        runtimeOnly(project(":libfdx:extensions:graphics:vulkan:platform:desktop_c"))
    }
}

tasks.register("test_desktop_c_opengl_generate_debug") {
    group = "application"
    description = "Generates the desktop_c OpenGL graphics test Debug project."
    dependsOn(":tests:platform:plugin:libfdx_desktop_c_opengl_generate_debug")
}

tasks.register("test_desktop_c_opengl_generate_release") {
    group = "application"
    description = "Generates the desktop_c OpenGL graphics test Release project."
    dependsOn(":tests:platform:plugin:libfdx_desktop_c_opengl_generate_release")
}

tasks.register("test_desktop_c_opengl_build_debug") {
    group = "application"
    description = "Builds the desktop_c OpenGL graphics test Debug executable."
    dependsOn(":tests:platform:plugin:libfdx_desktop_c_opengl_build_debug")
}

tasks.register("test_desktop_c_opengl_build_release") {
    group = "application"
    description = "Builds the desktop_c OpenGL graphics test Release executable."
    dependsOn(":tests:platform:plugin:libfdx_desktop_c_opengl_build_release")
}

tasks.register("test_desktop_c_opengl_run_debug") {
    group = "application"
    description = "Runs the desktop_c OpenGL graphics test Debug executable."
    dependsOn(":tests:platform:plugin:libfdx_desktop_c_opengl_run_debug")
}

tasks.register("test_desktop_c_opengl_run_release") {
    group = "application"
    description = "Runs the desktop_c OpenGL graphics test Release executable."
    dependsOn(":tests:platform:plugin:libfdx_desktop_c_opengl_run_release")
}

tasks.register("test_desktop_c_vulkan_generate_debug") {
    group = "application"
    description = "Generates the desktop_c Vulkan graphics test Debug project."
    dependsOn(":tests:platform:plugin:libfdx_desktop_c_vulkan_generate_debug")
}

tasks.register("test_desktop_c_vulkan_generate_release") {
    group = "application"
    description = "Generates the desktop_c Vulkan graphics test Release project."
    dependsOn(":tests:platform:plugin:libfdx_desktop_c_vulkan_generate_release")
}

tasks.register("test_desktop_c_vulkan_build_debug") {
    group = "application"
    description = "Builds the desktop_c Vulkan graphics test Debug executable."
    dependsOn(":tests:platform:plugin:libfdx_desktop_c_vulkan_build_debug")
}

tasks.register("test_desktop_c_vulkan_build_release") {
    group = "application"
    description = "Builds the desktop_c Vulkan graphics test Release executable."
    dependsOn(":tests:platform:plugin:libfdx_desktop_c_vulkan_build_release")
}

tasks.register("test_desktop_c_vulkan_run_debug") {
    group = "application"
    description = "Runs the desktop_c Vulkan graphics test Debug executable."
    dependsOn(":tests:platform:plugin:libfdx_desktop_c_vulkan_run_debug")
}

tasks.register("test_desktop_c_vulkan_run_release") {
    group = "application"
    description = "Runs the desktop_c Vulkan graphics test Release executable."
    dependsOn(":tests:platform:plugin:libfdx_desktop_c_vulkan_run_release")
}
