import org.gradle.api.tasks.Copy

plugins {
    id("maven-publish")
    id("java-library")
}


java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

val moduleName = "fdx_shared"

base {
    archivesName.set(moduleName)
}

val runtimeFdxSharedNativeResources = layout.buildDirectory.dir("generated/resources/runtimeFdxSharedNative")
val runtimeFdxSharedNativeSourceDir = layout.projectDirectory.dir("src/main/cpp")

val copyRuntimeFdxSharedNativeSources = tasks.register<Copy>("copy_runtime_fdx_shared_native_sources") {
    group = "libfdx native"
    description = "Copies shared runtime fdx native source payloads into fdx_shared generated resources."
    from(runtimeFdxSharedNativeSourceDir.dir("common")) {
        into("libfdx-native/common")
    }
    from(runtimeFdxSharedNativeSourceDir.dir("shader_compiler")) {
        into("libfdx-native/shared/shader_compiler")
    }
    from(runtimeFdxSharedNativeSourceDir.dir("runtime_fdx")) {
        into("libfdx-native/desktop/runtime_fdx")
    }
    into(runtimeFdxSharedNativeResources)
}

sourceSets {
    named("main") {
        resources.srcDir(runtimeFdxSharedNativeResources)
    }
}

tasks.named("processResources") {
    dependsOn(copyRuntimeFdxSharedNativeSources)
}
java {
    withSourcesJar()
    withJavadocJar()
}

tasks.named("sourcesJar") {
    dependsOn(copyRuntimeFdxSharedNativeSources)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = moduleName
            from(components["java"])
        }
    }
}
