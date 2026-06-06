import org.gradle.api.tasks.Copy

plugins {
    id("java-library")
}

repositories {
    google()
    mavenCentral()
    maven {
        url = uri("http://teavm.org/maven/repository/")
        isAllowInsecureProtocol = true
    }
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("backend_teavm_shared")
}

val runtimeFdxSharedNativeSourceDir =
    rootProject.layout.projectDirectory.dir("libfdx/runtime/fdx/platform/shared/src/main/cpp")
val generatedRuntimeFdxSharedNativeResources =
    layout.buildDirectory.dir("generated/resources/runtimeFdxSharedNative")

val copyRuntimeFdxSharedNativeResources =
    tasks.register<Copy>("copy_runtime_fdx_shared_native_resources") {
        group = "libfdx native"
        description = "Copies shared native resources into backend_teavm_shared resources."
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
