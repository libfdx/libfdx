plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "io.github.libfdx.gl"

base {
    archivesName.set("gl_desktop_native")
}

dependencies {
    api(project(":libfdx:extensions:graphics:gl:core"))
    runtimeOnly(project(":libfdx:backends:teavm_shared"))
}
