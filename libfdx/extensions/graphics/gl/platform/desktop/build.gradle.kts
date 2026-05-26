plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "io.github.libfdx.gl"

base {
    archivesName.set("gl_desktop")
}

dependencies {
    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()
    val lwjglNatives = when {
        osName.contains("windows") -> "natives-windows"
        osName.contains("linux") -> "natives-linux"
        osName.contains("mac") && (osArch.contains("aarch64") || osArch.contains("arm64")) -> "natives-macos-arm64"
        osName.contains("mac") -> "natives-macos"
        else -> throw GradleException("Unsupported LWJGL native platform: $osName/$osArch")
    }

    runtimeOnly(libs.lwjgl.opengl)
    runtimeOnly(variantOf(libs.lwjgl.opengl) { classifier(lwjglNatives) })
}
