
plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}


base {
    archivesName.set("tests_psp")
}

dependencies {
    implementation(project(":tests:core"))
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_psp:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:g2d:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:ui_kit:${libs.versions.libfdxSnapshot.get()}")
    } else {
        implementation(project(":libfdx:backends:psp"))
        implementation(project(":libfdx:framework:g2d"))
        implementation(project(":libfdx:framework:ui-kit"))
    }
}

tasks.register("test_psp_generate") {
    group = "application"
    description = "Generates the libfdx PSP shared test selector TeaVM C project."
    dependsOn(":tests:platform:plugin:libfdx_psp_test_generate")
}

tasks.register("test_psp_build") {
    group = "application"
    description = "Generates and builds the libfdx PSP shared test selector EBOOT project."
    dependsOn(":tests:platform:plugin:libfdx_psp_test_build")
}

tasks.register("test_psp_ppsspp_capture") {
    group = "application"
    description = "Builds the libfdx PSP shared test selector and captures a PPSSPP emulator frame."
    dependsOn(":tests:platform:plugin:libfdx_psp_test_ppsspp_capture")
}
