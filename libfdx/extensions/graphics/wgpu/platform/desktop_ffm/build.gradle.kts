plugins {
    id("java-library")
}

val java25 = JavaVersion.toVersion(25)

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    sourceCompatibility = java25
    targetCompatibility = java25
}

base {
    archivesName.set("wgpu_desktop_ffm")
}

dependencies {
    api(project(":libfdx:extensions:graphics:wgpu:core"))
    runtimeOnly(libs.jwebgpu.ffm)
    runtimeOnly(libs.jwebgpu.ffm.desktop)
}
