import io.github.libfdx.build.LibExt

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.gl"

base {
    archivesName.set("gl_desktop")
}

dependencies {
    api(libs.lwjgl.opengl)
    api(variantOf(libs.lwjgl.opengl) { classifier("natives-windows") })
    api(variantOf(libs.lwjgl.opengl) { classifier("natives-linux") })
    api(variantOf(libs.lwjgl.opengl) { classifier("natives-macos") })
    api(variantOf(libs.lwjgl.opengl) { classifier("natives-macos-arm64") })
}
