plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("backend_web")
}

dependencies {
    implementation(project(":libfdx:backends:teavm_shared"))
    implementation(project(":libfdx:tools:font"))

    api(project(":libfdx:foundation:core"))
    api(project(":libfdx:runtime:application"))
    api(project(":libfdx:runtime:display"))
    api(project(":libfdx:runtime:files"))
    api(project(":libfdx:runtime:core"))
    api(project(":libfdx:graphics:api"))

    implementation(libs.teavm.jso)
    implementation(libs.teavm.jso.apis)
    implementation(libs.teavm.jso.impl)
    implementation("org.teavm:teavm-platform:${libs.versions.teavm.get()}")
    compileOnly("org.teavm:teavm-core:${libs.versions.teavm.get()}")
}
