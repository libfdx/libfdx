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
        api("${LibExt.fdxGroup}:camera:${LibExt.publishedLibfdxVersion}")
        api("${LibExt.fdxGroup}:g2d:${LibExt.publishedLibfdxVersion}")
        api("${LibExt.fdxGroup}:g3d:${LibExt.publishedLibfdxVersion}")
        api("${LibExt.fdxGroup}:ui_kit:${LibExt.publishedLibfdxVersion}")
        api("${LibExt.fdxGroup}:scenario_validator:${LibExt.publishedLibfdxVersion}")
        api("${LibExt.fdxGroup}:scenario_validator_ui_kit:${LibExt.publishedLibfdxVersion}")
    } else {
        api(project(":libfdx:framework:application"))
        api(project(":libfdx:framework:input"))
        api(project(":libfdx:framework:graphics"))
        api(project(":libfdx:framework:camera"))
        api(project(":libfdx:framework:g2d"))
        api(project(":libfdx:framework:g3d"))
        api(project(":libfdx:framework:ui-kit"))
        api(project(":libfdx:validation:scenario-validator"))
        api(project(":libfdx:validation:scenario-validator-ui-kit"))
    }

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
