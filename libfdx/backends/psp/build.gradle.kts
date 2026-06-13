plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("backend_psp")
}

dependencies {
    implementation(project(":libfdx:backends:c_shared"))
    implementation(project(":libfdx:tools:font"))
    api(project(":libfdx:runtime:application"))
    api(project(":libfdx:graphics:api"))

    api(libs.teavm.interop)
    compileOnly("org.teavm:teavm-core:${libs.versions.teavm.get()}")

    runtimeOnly(project(":libfdx:runtime:fdx:platform:shared"))
}
