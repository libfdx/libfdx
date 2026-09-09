import java.security.MessageDigest
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
    withSourcesJar()
    withJavadocJar()
}


val moduleName = "d3d12_core"

base {
    archivesName.set(moduleName)
}

dependencies {
    api(project(":libfdx:framework:fdx:core"))
    api(project(":libfdx:framework:graphics"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

// Provider-owned compiler distribution. Applications never download native code at startup.
val dxcVersion = "1.9.2607"
val dxcChecksum = "a1dfb116ba3eeae6a1582291b53a8e7bf65ad760676bd3194685c8f7367cd241"
val dxcArchive = layout.buildDirectory.file("dxc/dxc_2026_07_29.zip")
val dxcResources = layout.buildDirectory.dir("generated/dxc-resources")
val packageDxc by tasks.registering {
    inputs.property("version", dxcVersion)
    inputs.property("sha256", dxcChecksum)
    outputs.dir(dxcResources)
    doLast {
        fun checksum(file: File): String = MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        val archive = dxcArchive.get().asFile
        if (!archive.isFile || checksum(archive) != dxcChecksum) {
            archive.parentFile.mkdirs()
            val download = File(archive.parentFile, "download.zip")
            URI("https://github.com/microsoft/DirectXShaderCompiler/releases/download/v$dxcVersion/${archive.name}")
                .toURL().openStream().use { input -> download.outputStream().use { input.copyTo(it) } }
            check(checksum(download) == dxcChecksum) { "DXC package SHA-256 mismatch" }
            Files.move(download.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        val destination = dxcResources.get().dir("libfdx-dxc/windows-x64").asFile
        destination.mkdirs()
        val manifest = Properties()
        manifest.setProperty("version", dxcVersion)
        ZipFile(archive).use { zip ->
            for (name in listOf("bin/x64/dxcompiler.dll", "bin/x64/dxil.dll", "LICENCE-MIT.txt", "LICENSE-LLVM.txt", "LICENSE-MS.txt")) {
                val entry = zip.getEntry(name.replace('/', '\\')) ?: error("DXC package is missing $name")
                val output = File(destination, name.substringAfterLast('/'))
                zip.getInputStream(entry).use { input -> output.outputStream().use { input.copyTo(it) } }
                manifest.setProperty(output.name, checksum(output))
            }
        }
        File(destination, "distribution.properties").writeText(
            manifest.stringPropertyNames().sorted().joinToString("\n", postfix = "\n") { "$it=${manifest.getProperty(it)}" }
        )
    }
}
sourceSets.main { resources.srcDir(packageDxc) }

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = moduleName
            from(components["java"])
        }
    }
}
