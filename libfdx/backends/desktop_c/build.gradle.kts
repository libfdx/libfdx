import org.gradle.api.tasks.Copy

plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

val moduleName = "backend_desktop_c"

base {
    archivesName.set(moduleName)
}

val runtimeFdxSharedNativeSourceDir = rootProject.layout.projectDirectory.dir("libfdx/framework/fdx/platform/shared/src/main/cpp")
val generatedRuntimeFdxNativeResources = layout.buildDirectory.dir("generated/resources/runtimeFdxNative")

val copyRuntimeFdxDesktopCSources = tasks.register<Copy>("copy_runtime_fdx_desktop_c_sources") {
    group = "libfdx native"
    description = "Copies shared runtime fdx native sources into backend_desktop_c resources."
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
    dependsOn(copyRuntimeFdxDesktopCSources)
}

dependencies {
    implementation(project(":libfdx:backends:c_shared"))

    api(project(":libfdx:framework:fdx:core"))
    api(project(":libfdx:framework:application"))
    api(project(":libfdx:framework:display"))
    api(project(":libfdx:framework:files"))
    api(project(":libfdx:framework:input"))
    api(project(":libfdx:framework:graphics"))
    api(project(":libfdx:extensions:graphics:gl:core"))
    api(project(":libfdx:extensions:graphics:vulkan:core"))
    api(libs.teavm.interop)

    runtimeOnly(project(":libfdx:framework:fdx:platform:shared"))
}
java {
    withSourcesJar()
    withJavadocJar()
}

tasks.named("sourcesJar") {
    dependsOn(copyRuntimeFdxDesktopCSources)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = moduleName
            from(components["java"])
        }
    }
}
