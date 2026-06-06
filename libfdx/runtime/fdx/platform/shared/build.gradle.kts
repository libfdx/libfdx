import java.net.URI
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("fdx_shared")
}

val runtimeFdxSharedNativeResources = layout.buildDirectory.dir("generated/resources/runtimeFdxSharedNative")
val runtimeFdxSharedNativeSourceDir = layout.projectDirectory.dir("src/main/cpp")
val freetypeVersion = "2.14.3"
val freetypeArchiveName = "freetype-$freetypeVersion.tar.xz"
val freetypeSourceUrl = "https://download.savannah.gnu.org/releases/freetype/$freetypeArchiveName"
val freetypeArchive = layout.buildDirectory.file("third-party/downloads/$freetypeArchiveName")
val freetypeExtractDir = layout.buildDirectory.dir("third-party/freetype")
val freetypeSourceDir = freetypeExtractDir.map { it.dir("freetype-$freetypeVersion") }

val copyRuntimeFdxSharedNativeSources = tasks.register<Copy>("copy_runtime_fdx_shared_native_sources") {
    group = "libfdx native"
    description = "Copies shared runtime fdx native source payloads into fdx_shared generated resources."
    from(runtimeFdxSharedNativeSourceDir.dir("common")) {
        into("libfdx-native/common")
    }
    from(runtimeFdxSharedNativeSourceDir.dir("runtime_fdx")) {
        into("libfdx-native/desktop/runtime_fdx")
    }
    into(runtimeFdxSharedNativeResources)
}

val downloadFreetypeSource = tasks.register("download_freetype_source") {
    group = "libfdx native"
    description = "Downloads FreeType source used to build runtime fdx native font support."
    outputs.file(freetypeArchive)
    doLast {
        val output = freetypeArchive.get().asFile
        output.parentFile.mkdirs()
        if (!output.isFile) {
            URI(freetypeSourceUrl).toURL().openStream().use { input ->
                output.outputStream().use { outputStream ->
                    input.copyTo(outputStream)
                }
            }
        }
    }
}

val extractFreetypeSource = tasks.register<Exec>("extract_freetype_source") {
    group = "libfdx native"
    description = "Extracts FreeType source into build/third-party for native runtime fdx builds."
    dependsOn(downloadFreetypeSource)
    outputs.dir(freetypeSourceDir)
    doFirst {
        freetypeExtractDir.get().asFile.mkdirs()
    }
    executable = "tar"
    args("-xf", freetypeArchive.get().asFile.absolutePath, "-C", freetypeExtractDir.get().asFile.absolutePath)
}

tasks.register("prepare_runtime_fdx_shared") {
    group = "libfdx native"
    description = "Prepares third-party native dependencies used by runtime fdx platform builds."
    dependsOn(extractFreetypeSource)
}

sourceSets {
    named("main") {
        resources.srcDir(runtimeFdxSharedNativeResources)
    }
}

tasks.named("processResources") {
    dependsOn(copyRuntimeFdxSharedNativeSources)
}
