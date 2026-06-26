import io.github.libfdx.build.LibExt

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.samples.multiplayer"

base {
    archivesName.set("sample_multiplayer_2d_webrtc_core")
}

dependencies {
    if (LibExt.usePublishedLibfdx) {
        api("${LibExt.fdxGroup}:application:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:graphics:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:g2d:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:ui_kit:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:net:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:webrtc_core:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:scenario_validator:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:scenario_validator_ui_kit:${LibExt.publishedLibfdxVersion}")
    } else {
        api(project(":libfdx:runtime:application"))
        implementation(project(":libfdx:graphics:api"))
        implementation(project(":libfdx:graphics:g2d"))
        implementation(project(":libfdx:ui:ui-kit"))
        implementation(project(":libfdx:runtime:net"))
        implementation(project(":libfdx:extensions:net:webrtc:core"))
        implementation(project(":libfdx:validation:scenario-validator"))
        implementation(project(":libfdx:validation:scenario-validator-ui-kit"))
    }
}
