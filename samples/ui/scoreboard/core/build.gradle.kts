plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("sample_scoreboard_core")
}

dependencies {
    api(project(":libfdx:framework:application"))
    implementation(project(":libfdx:framework:ui-kit"))
    implementation(project(":libfdx:framework:storage"))
}
