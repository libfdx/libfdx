
plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}


base {
    archivesName.set("sample_multiplayer_2d_webrtc_core")
}

val libfdxDependencyVersion =
    gradle.extensions.extraProperties.get("libfdxDependencyVersion") as String

dependencies {
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        api("${libs.versions.libfdxGroup.get()}:application:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:graphics:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:g2d:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:ui_kit:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:net:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:webrtc_core:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:scenario_validator:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:scenario_validator_ui_kit:$libfdxDependencyVersion")
    } else {
        api(project(":libfdx:framework:application"))
        implementation(project(":libfdx:framework:graphics"))
        implementation(project(":libfdx:framework:g2d"))
        implementation(project(":libfdx:framework:ui-kit"))
        implementation(project(":libfdx:framework:net"))
        implementation(project(":libfdx:extensions:net:webrtc:core"))
        implementation(project(":libfdx:extensions:scenario_validator:core"))
        implementation(project(":libfdx:extensions:scenario_validator:ui-kit"))
    }
}
