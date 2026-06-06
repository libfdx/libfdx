import org.gradle.api.tasks.Copy

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("backend_desktop_native")
}

val runtimeFdxSharedNativeSourceDir = rootProject.layout.projectDirectory.dir("libfdx/runtime/fdx/platform/shared/src/main/cpp")
val generatedRuntimeFdxNativeResources = layout.buildDirectory.dir("generated/resources/runtimeFdxNative")

val copyRuntimeFdxDesktopNativeSources = tasks.register<Copy>("copy_runtime_fdx_desktop_native_sources") {
    group = "libfdx native"
    description = "Copies shared runtime fdx native sources into backend_desktop_native resources."
    from(runtimeFdxSharedNativeSourceDir.dir("runtime_fdx")) {
        into("libfdx-native/desktop/runtime_fdx")
    }
    into(generatedRuntimeFdxNativeResources)
}

sourceSets {
    named("main") {
        resources.srcDir(generatedRuntimeFdxNativeResources)
    }
}

tasks.named("processResources") {
    dependsOn(copyRuntimeFdxDesktopNativeSources)
}

dependencies {
    implementation(project(":libfdx:backends:teavm_shared"))

    api(project(":libfdx:runtime:fdx:core"))
    api(project(":libfdx:runtime:application"))
    api(project(":libfdx:runtime:display"))
    api(project(":libfdx:runtime:files"))
    api(project(":libfdx:runtime:input"))
    api(project(":libfdx:graphics:api"))
    api(project(":libfdx:extensions:graphics:gl:core"))
    api(project(":libfdx:extensions:graphics:vulkan:core"))
    api(libs.teavm.interop)

    runtimeOnly(project(":libfdx:runtime:fdx:platform:shared"))
}
