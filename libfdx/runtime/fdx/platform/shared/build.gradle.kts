import io.github.libfdx.build.LibExt
import java.io.File
import java.net.URI
import java.security.MessageDigest
import org.gradle.api.GradleException
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec

plugins {
    id("java-library")
}

group = "${LibExt.fdxGroup}.runtime.fdx"

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
val freetypeArchiveSha256 = "36bc4f1cc413335368ee656c42afca65c5a3987e8768cc28cf11ba775e785a5f"
val freetypeSourceUrls = listOf(
    "https://download-mirror.savannah.gnu.org/releases/freetype/$freetypeArchiveName",
    "https://downloads.sourceforge.net/project/freetype/freetype2/$freetypeVersion/$freetypeArchiveName",
    "https://download.savannah.gnu.org/releases/freetype/$freetypeArchiveName",
)
val freetypeArchive = layout.buildDirectory.file("third-party/downloads/$freetypeArchiveName")
val freetypeExtractDir = layout.buildDirectory.dir("third-party/freetype")
val freetypeSourceDir = freetypeExtractDir.map { it.dir("freetype-$freetypeVersion") }

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

fun verifyFreetypeArchive(file: File) {
    val actual = sha256(file)
    if (actual != freetypeArchiveSha256) {
        throw GradleException(
            "FreeType archive checksum mismatch for ${file.absolutePath}. Expected $freetypeArchiveSha256 but got $actual."
        )
    }
}

fun isExpectedFreetypeArchive(file: File): Boolean {
    return file.isFile && sha256(file) == freetypeArchiveSha256
}

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

val downloadFreetypeSource = tasks.register("download_freetype_source") {
    group = "libfdx native"
    description = "Downloads FreeType source used to build runtime fdx native font support."
    outputs.file(freetypeArchive)
    outputs.upToDateWhen {
        isExpectedFreetypeArchive(freetypeArchive.get().asFile)
    }
    doLast {
        val output = freetypeArchive.get().asFile
        output.parentFile.mkdirs()
        if (isExpectedFreetypeArchive(output)) {
            return@doLast
        }
        if (output.exists()) {
            logger.warn("Deleting invalid FreeType archive before redownloading: ${output.absolutePath}")
            output.delete()
        }

        val temporaryOutput = output.resolveSibling("${output.name}.tmp")
        var lastFailure: Exception? = null
        for (sourceUrl in freetypeSourceUrls) {
            temporaryOutput.delete()
            try {
                logger.lifecycle("Downloading FreeType $freetypeVersion from $sourceUrl")
                URI(sourceUrl).toURL().openStream().use { input ->
                    temporaryOutput.outputStream().use { outputStream ->
                        input.copyTo(outputStream)
                    }
                }
                verifyFreetypeArchive(temporaryOutput)
                if (output.exists()) {
                    output.delete()
                }
                if (!temporaryOutput.renameTo(output)) {
                    temporaryOutput.copyTo(output, overwrite = true)
                    temporaryOutput.delete()
                }
                return@doLast
            } catch (exception: Exception) {
                lastFailure = exception
                temporaryOutput.delete()
                logger.warn("Failed to download FreeType from $sourceUrl: ${exception.message}")
            }
        }
        throw GradleException("Could not download FreeType $freetypeVersion from any configured source.", lastFailure)
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
