
plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}



base {
    archivesName.set("tests_core")
}

dependencies {
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        api("${libs.versions.libfdxGroup.get()}:application:${libs.versions.libfdxSnapshot.get()}")
        api("${libs.versions.libfdxGroup.get()}:input:${libs.versions.libfdxSnapshot.get()}")
        api("${libs.versions.libfdxGroup.get()}:graphics:${libs.versions.libfdxSnapshot.get()}")
        api("${libs.versions.libfdxGroup.get()}:camera:${libs.versions.libfdxSnapshot.get()}")
        api("${libs.versions.libfdxGroup.get()}:g2d:${libs.versions.libfdxSnapshot.get()}")
        api("${libs.versions.libfdxGroup.get()}:g3d:${libs.versions.libfdxSnapshot.get()}")
        api("${libs.versions.libfdxGroup.get()}:shader_graph_g2d:${libs.versions.libfdxSnapshot.get()}")
        api("${libs.versions.libfdxGroup.get()}:shader_graph_g3d:${libs.versions.libfdxSnapshot.get()}")
        api("${libs.versions.libfdxGroup.get()}:shader_graph_ui_kit:${libs.versions.libfdxSnapshot.get()}")
        api("${libs.versions.libfdxGroup.get()}:ui_kit:${libs.versions.libfdxSnapshot.get()}")
        api("${libs.versions.libfdxGroup.get()}:scenario_validator:${libs.versions.libfdxSnapshot.get()}")
        api("${libs.versions.libfdxGroup.get()}:scenario_validator_ui_kit:${libs.versions.libfdxSnapshot.get()}")
    } else {
        api(project(":libfdx:framework:application"))
        api(project(":libfdx:framework:input"))
        api(project(":libfdx:framework:graphics"))
        api(project(":libfdx:framework:camera"))
        api(project(":libfdx:framework:g2d"))
        api(project(":libfdx:framework:g3d"))
        api(project(":libfdx:extensions:graphics:shader-graph:g2d"))
        api(project(":libfdx:extensions:graphics:shader-graph:g3d"))
        api(project(":libfdx:extensions:graphics:shader-graph:ui-kit"))
        api(project(":libfdx:framework:ui-kit"))
        api(project(":libfdx:extensions:scenario_validator:core"))
        api(project(":libfdx:extensions:scenario_validator:ui-kit"))
    }

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
