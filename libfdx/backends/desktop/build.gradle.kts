import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("backend_desktop")
}

sourceSets {
    named("main") {
        resources.srcDir(project(":libfdx:runtime:core").layout.buildDirectory.dir("generated/resources/runtimeCoreDesktop"))
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(":libfdx:runtime:core:copy_desktop_runtime_core_native")
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

    api(project(":libfdx:foundation:core"))
    implementation(project(":libfdx:foundation:math"))
    api(project(":libfdx:assets:manager"))
    api(project(":libfdx:runtime:application"))
    api(project(":libfdx:runtime:display"))
    api(project(":libfdx:runtime:files"))
    api(project(":libfdx:runtime:input"))
    api(project(":libfdx:runtime:core"))
    api(project(":libfdx:graphics:api"))
    api(project(":libfdx:extensions:graphics:gl:core"))
    api(project(":libfdx:extensions:graphics:vulkan:core"))

    implementation(libs.lwjgl)
    implementation(libs.lwjgl.freetype)
    implementation(libs.lwjgl.glfw)
    compileOnly(libs.lwjgl.opengl)
    compileOnly(libs.lwjgl.vulkan)

    runtimeOnly(variantOf(libs.lwjgl) { classifier(lwjglNatives) })
    runtimeOnly(variantOf(libs.lwjgl.freetype) { classifier(lwjglNatives) })
    runtimeOnly(variantOf(libs.lwjgl.glfw) { classifier(lwjglNatives) })
}
