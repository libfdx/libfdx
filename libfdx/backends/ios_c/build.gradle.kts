plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("backend_ios_c")
}

dependencies {
    implementation(project(":libfdx:backends:c_shared"))

    api(project(":libfdx:runtime:fdx:core"))
    api(project(":libfdx:runtime:application"))
    api(project(":libfdx:runtime:display"))
    api(project(":libfdx:runtime:files"))
    api(project(":libfdx:runtime:input"))
    api(project(":libfdx:graphics:api"))
    api(project(":libfdx:extensions:graphics:gl:core"))
    api(libs.teavm.interop)

    runtimeOnly(project(":libfdx:runtime:fdx:platform:shared"))
}
