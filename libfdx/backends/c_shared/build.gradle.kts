import org.gradle.api.tasks.Copy

plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

val moduleName = "backend_c_shared"

base {
    archivesName.set(moduleName)
}

val runtimeFdxSharedNativeSourceDir =
    rootProject.layout.projectDirectory.dir("libfdx/runtime/fdx/platform/shared/src/main/cpp")
val generatedRuntimeFdxSharedNativeResources =
    layout.buildDirectory.dir("generated/resources/runtimeFdxSharedNative")

val copyRuntimeFdxSharedNativeResources =
    tasks.register<Copy>("copy_runtime_fdx_shared_native_resources") {
        group = "libfdx native"
        description = "Copies shared native resources into backend_c_shared resources."
        from(runtimeFdxSharedNativeSourceDir.dir("common/native_optimizations")) {
            into("libfdx-native/shared/native_optimizations")
        }
        from(runtimeFdxSharedNativeSourceDir.dir("common/stb")) {
            into("libfdx-native/shared/stb")
        }
        into(generatedRuntimeFdxSharedNativeResources)
    }

sourceSets {
    named("main") {
        resources.srcDir(generatedRuntimeFdxSharedNativeResources)
    }
}

tasks.named("processResources") {
    dependsOn(copyRuntimeFdxSharedNativeResources)
}

dependencies {
    api(libs.teavm.tooling)
    api(libs.teavm.classlib)
}
java {
    withSourcesJar()
    withJavadocJar()
}

tasks.named("sourcesJar") {
    dependsOn(copyRuntimeFdxSharedNativeResources)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = moduleName
            from(components["java"])
        }
    }
}
