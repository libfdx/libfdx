plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("wgpu_desktop_jni")
}

dependencies {
    api(project(":libfdx:extensions:graphics:wgpu:core"))
    runtimeOnly(libs.jwebgpu.jni)
    runtimeOnly(libs.jwebgpu.jni.desktop)
}
