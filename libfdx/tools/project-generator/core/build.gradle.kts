import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("project_generator_core")
}

val libfdxGroup = libs.versions.libfdxGroup.get()
val libfdxReleaseVersion = libs.versions.libfdxRelease.get()
val libfdxSnapshotVersion = libs.versions.libfdxSnapshot.get()
val explicitGeneratorVersion = providers.gradleProperty("libfdx.projectGenerator.version")
    .orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
val explicitReleaseGenerator = providers.gradleProperty("libfdx.projectGenerator.release")
    .orNull
    ?.trim()
    ?.equals("true", ignoreCase = true) == true
val pagesDeploymentRef = providers.environmentVariable("LIBFDX_REF")
    .orNull
    ?.trim()
    ?.removePrefix("refs/tags/")
val releaseGenerator = explicitReleaseGenerator
    || pagesDeploymentRef == libfdxReleaseVersion
    || pagesDeploymentRef == "v$libfdxReleaseVersion"
val bundledLibfdxVersion = explicitGeneratorVersion
    ?: if (releaseGenerator) libfdxReleaseVersion else libfdxSnapshotVersion
val bundledChannel = when {
    explicitGeneratorVersion != null -> "custom"
    releaseGenerator -> "release"
    else -> "snapshot"
}

val samplesDirectory = rootProject.layout.projectDirectory.dir("samples").asFile
val ignoredSampleDirectories = setOf("build", ".gradle", ".git", ".idea", "out", "node_modules")
val defaultSampleId = "base/starter-project"
val knownPlatforms = linkedMapOf(
    "desktop" to "DESKTOP",
    "android" to "ANDROID",
    "web" to "WEB",
    "desktop_c" to "DESKTOP_C",
    "ios_c" to "IOS_C"
)

fun sampleFiles(root: File): List<File> {
    return root.walkTopDown()
        .onEnter { directory -> directory == root || directory.name !in ignoredSampleDirectories }
        .filter { file ->
            file.isFile
                && file.name != "local.properties"
                && file.name != ".DS_Store"
        }
        .sortedBy { it.relativeTo(root).path.replace(File.separatorChar, '/') }
        .toList()
}

fun sampleId(sampleRoot: File): String =
    sampleRoot.relativeTo(samplesDirectory).path.replace(File.separatorChar, '/')

val bundledSampleRoots = samplesDirectory.walkTopDown()
    .onEnter { directory -> directory == samplesDirectory || directory.name !in ignoredSampleDirectories }
    .filter { directory -> directory.isDirectory && directory.resolve("core/build.gradle.kts").isFile }
    .sortedWith(compareBy<File>(
        { if (sampleId(it) == defaultSampleId) 0 else 1 },
        { sampleId(it) }
    ))
    .toList()

fun javaString(value: String): String {
    val escaped = StringBuilder(value.length + 16)
    value.forEach { character ->
        when (character) {
            '\\' -> escaped.append("\\\\")
            '"' -> escaped.append("\\\"")
            '\n' -> escaped.append("\\n")
            '\r' -> escaped.append("\\r")
            '\t' -> escaped.append("\\t")
            else -> {
                if (character.code < 32 || character.code > 126) {
                    escaped.append(String.format("\\u%04x", character.code))
                } else {
                    escaped.append(character)
                }
            }
        }
    }
    return escaped.toString()
}

fun displayName(sampleRoot: File): String {
    val heading = sampleRoot.resolve("README.md")
        .takeIf(File::isFile)
        ?.useLines { lines -> lines.firstOrNull { it.trim().startsWith("# ") } }
        ?.trim()
        ?.removePrefix("# ")
        ?.trim()
    if (!heading.isNullOrEmpty()) {
        return heading
    }
    return sampleRoot.name
        .split('-', '_')
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
}

fun description(sampleRoot: File): String {
    val readme = sampleRoot.resolve("README.md")
    if (!readme.isFile) {
        return "Bundled libFDX sample."
    }
    val lines = readme.readLines()
    var headingSeen = false
    val paragraph = ArrayList<String>()
    for (rawLine in lines) {
        val line = rawLine.trim()
        if (!headingSeen) {
            headingSeen = line.startsWith("# ")
            continue
        }
        if (line.isEmpty()) {
            if (paragraph.isNotEmpty()) {
                break
            }
            continue
        }
        if (line.startsWith("#") || line.startsWith("- ") || line.startsWith("```")) {
            if (paragraph.isNotEmpty()) {
                break
            }
            continue
        }
        paragraph.add(line)
    }
    return paragraph.joinToString(" ").ifEmpty { "Bundled libFDX sample." }
}

fun zipSample(sampleRoot: File): ByteArray {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
        sampleFiles(sampleRoot).forEach { file ->
            val path = file.relativeTo(sampleRoot).path.replace(File.separatorChar, '/')
            val entry = ZipEntry(path)
            entry.time = 0L
            zip.putNextEntry(entry)
            file.inputStream().use { input -> input.copyTo(zip) }
            zip.closeEntry()
        }
    }
    return bytes.toByteArray()
}

val generatedBundledSamplesDirectory =
    layout.buildDirectory.dir("generated/sources/bundled-samples/java")

val generateBundledSamples = tasks.register("generate_bundled_project_samples") {
    group = "build"
    description = "Embeds every repository sample and the selected libFDX version into the project generator."
    inputs.files(bundledSampleRoots.flatMap(::sampleFiles))
    inputs.file(rootProject.layout.projectDirectory.file("gradle/libs.versions.toml"))
    inputs.property("libfdxGroup", libfdxGroup)
    inputs.property("libfdxReleaseVersion", libfdxReleaseVersion)
    inputs.property("libfdxVersion", bundledLibfdxVersion)
    inputs.property("generatorChannel", bundledChannel)
    outputs.dir(generatedBundledSamplesDirectory)

    doLast {
        val outputRoot = generatedBundledSamplesDirectory.get().asFile
        delete(outputRoot)
        val packageDirectory = outputRoot.resolve(
            "io/github/libfdx/tools/project/generator")
        packageDirectory.mkdirs()

        val catalogText = rootProject.layout.projectDirectory
            .file("gradle/libs.versions.toml")
            .asFile
            .readText()
            .replace(
                Regex("""(?m)^libfdxSnapshot\s*=\s*["'][^"']*["']\s*$"""),
                """libfdxSnapshot = "$bundledLibfdxVersion""""
            )

        val catalogSource = StringBuilder()
        catalogSource.appendLine("package io.github.libfdx.tools.project.generator;")
        catalogSource.appendLine()
        catalogSource.appendLine("final class BundledSampleCatalogData {")
        catalogSource.appendLine(
            "    static final String LIBFDX_GROUP = \"${javaString(libfdxGroup)}\";")
        catalogSource.appendLine(
            "    static final String LIBFDX_VERSION = \"${javaString(bundledLibfdxVersion)}\";")
        catalogSource.appendLine(
            "    static final String LIBFDX_RELEASE_VERSION = \"${javaString(libfdxReleaseVersion)}\";")
        catalogSource.appendLine(
            "    static final String CHANNEL = \"${javaString(bundledChannel)}\";")
        catalogSource.appendLine()
        catalogSource.appendLine("    private BundledSampleCatalogData() {")
        catalogSource.appendLine("    }")
        catalogSource.appendLine()
        catalogSource.appendLine("    static ProjectSample[] samples() {")
        catalogSource.appendLine("        return new ProjectSample[] {")
        bundledSampleRoots.forEachIndexed { index, sampleRoot ->
            val id = sampleId(sampleRoot)
            val comma = if (index + 1 < bundledSampleRoots.size) "," else ""
            val platforms = knownPlatforms
                .filterKeys { directory ->
                    sampleRoot.resolve("platform/$directory/build.gradle.kts").isFile
                }
                .values
                .joinToString(", ") { value -> "ProjectPlatform.$value" }
            catalogSource.appendLine(
                "            new ProjectSample(\"${javaString(id)}\", " +
                    "\"${javaString(displayName(sampleRoot))}\", " +
                    "\"${javaString(description(sampleRoot))}\", " +
                    "new ProjectPlatform[] { $platforms })$comma")
        }
        catalogSource.appendLine("        };")
        catalogSource.appendLine("    }")
        catalogSource.appendLine()
        catalogSource.appendLine("    static String versionCatalog() {")
        catalogSource.appendLine("        return \"${javaString(catalogText)}\";")
        catalogSource.appendLine("    }")
        catalogSource.appendLine()
        catalogSource.appendLine("    static byte[] archive(String sampleId) {")
        bundledSampleRoots.forEachIndexed { index, sampleRoot ->
            val id = sampleId(sampleRoot)
            catalogSource.appendLine(
                "        if (\"${javaString(id)}\".equals(sampleId)) {")
            catalogSource.appendLine("            return BundledSampleArchive$index.bytes();")
            catalogSource.appendLine("        }")
        }
        catalogSource.appendLine(
            "        throw new IllegalArgumentException(\"Unknown bundled sample: \" + sampleId);")
        catalogSource.appendLine("    }")
        catalogSource.appendLine("}")
        packageDirectory.resolve("BundledSampleCatalogData.java")
            .writeText(catalogSource.toString())

        bundledSampleRoots.forEachIndexed { index, sampleRoot ->
            val encoded = Base64.getEncoder().encodeToString(zipSample(sampleRoot))
            val chunks = encoded.chunked(12_000)
            val archiveSource = StringBuilder()
            archiveSource.appendLine("package io.github.libfdx.tools.project.generator;")
            archiveSource.appendLine()
            archiveSource.appendLine("import java.util.Base64;")
            archiveSource.appendLine()
            archiveSource.appendLine("final class BundledSampleArchive$index {")
            archiveSource.appendLine("    private static final String[] CHUNKS = {")
            chunks.forEachIndexed { chunkIndex, chunk ->
                val comma = if (chunkIndex + 1 < chunks.size) "," else ""
                archiveSource.appendLine("        \"$chunk\"$comma")
            }
            archiveSource.appendLine("    };")
            archiveSource.appendLine()
            archiveSource.appendLine("    private BundledSampleArchive$index() {")
            archiveSource.appendLine("    }")
            archiveSource.appendLine()
            archiveSource.appendLine("    static byte[] bytes() {")
            archiveSource.appendLine("        StringBuilder encoded = new StringBuilder();")
            archiveSource.appendLine("        for (int i = 0; i < CHUNKS.length; i++) {")
            archiveSource.appendLine("            encoded.append(CHUNKS[i]);")
            archiveSource.appendLine("        }")
            archiveSource.appendLine("        return Base64.getDecoder().decode(encoded.toString());")
            archiveSource.appendLine("    }")
            archiveSource.appendLine("}")
            packageDirectory.resolve("BundledSampleArchive$index.java")
                .writeText(archiveSource.toString())
        }
    }
}

sourceSets {
    main {
        java.srcDir(generatedBundledSamplesDirectory)
    }
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(generateBundledSamples)
}

tasks.withType<Jar>().configureEach {
    manifest.attributes(
        "Implementation-Title" to "libFDX Project Generator",
        "Implementation-Version" to bundledLibfdxVersion,
        "Libfdx-Generator-Channel" to bundledChannel
    )
}

tasks.register<JavaExec>("test_generate_project") {
    group = "verification"
    description = "Runs the project generator core smoke checks."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.libfdx.tools.project.generator.ProjectGeneratorSmokeTest")
}

tasks.named<Test>("test") {
    enabled = false
}

tasks.named("check") {
    dependsOn("test_generate_project")
}
