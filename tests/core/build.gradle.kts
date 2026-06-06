import io.github.libfdx.build.LibExt

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.tests"


base {
    archivesName.set("tests_core")
}

dependencies {
    if (LibExt.usePublishedLibfdx) {
        api("${LibExt.fdxGroup}:application:${LibExt.publishedLibfdxVersion}")
        api("${LibExt.fdxGroup}:input:${LibExt.publishedLibfdxVersion}")
        api("${LibExt.fdxGroup}:graphics:${LibExt.publishedLibfdxVersion}")
        api("${LibExt.fdxGroup}:g2d:${LibExt.publishedLibfdxVersion}")
        api("${LibExt.fdxGroup}:g3d:${LibExt.publishedLibfdxVersion}")
        api("${LibExt.fdxGroup}:ui_kit:${LibExt.publishedLibfdxVersion}")
        api("${LibExt.fdxGroup}:scenario_validator:${LibExt.publishedLibfdxVersion}")
        api("${LibExt.fdxGroup}:scenario_validator_ui_kit:${LibExt.publishedLibfdxVersion}")
    } else {
        api(project(":libfdx:runtime:application"))
        api(project(":libfdx:runtime:input"))
        api(project(":libfdx:graphics:api"))
        api(project(":libfdx:graphics:g2d"))
        api(project(":libfdx:graphics:g3d"))
        api(project(":libfdx:ui:ui-kit"))
        api(project(":libfdx:validation:scenario-validator"))
        api(project(":libfdx:validation:scenario-validator-ui-kit"))
    }
}
