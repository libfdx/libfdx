import io.github.libfdx.build.LibExt
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Locale
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.plugins.signing.SigningExtension
import groovy.util.Node

val libfdxName = "libfdx"
val snapshotRepositoryUrl = "https://central.sonatype.com/repository/maven-snapshots/"
val taskNames = gradle.startParameter.taskNames
val libfdxBaseVersion = LibExt.fdxVersion
val libfdxGroup = LibExt.fdxGroup
val publishTargetProperty = "libfdxPublishTarget"
val gradlePluginDependencyArtifactsProperty = "libfdxGradlePluginDependencyArtifacts"
val publishTarget = if(extensions.extraProperties.has(publishTargetProperty)) {
    extensions.extraProperties.get(publishTargetProperty).toString()
} else {
    throw GradleException("$publishTargetProperty must be configured before applying publish.gradle.kts")
}

val libfdxPublishableProjectPaths = listOf(
    ":libfdx:foundation:math",
    ":libfdx:foundation:json",
    ":libfdx:foundation:collections",
    ":libfdx:runtime:fdx:core",
    ":libfdx:runtime:fdx:platform:shared",
    ":libfdx:runtime:fdx:platform:desktop",
    ":libfdx:runtime:fdx:platform:android",
    ":libfdx:runtime:fdx:platform:web",
    ":libfdx:runtime:application",
    ":libfdx:runtime:display",
    ":libfdx:runtime:files",
    ":libfdx:runtime:input",
    ":libfdx:runtime:net",
    ":libfdx:runtime:storage",
    ":libfdx:assets:manager",
    ":libfdx:assets:loaders",
    ":libfdx:graphics:api",
    ":libfdx:graphics:camera",
    ":libfdx:graphics:g2d",
    ":libfdx:graphics:g3d",
    ":libfdx:ui:ui-kit",
    ":libfdx:validation:scenario-validator",
    ":libfdx:validation:scenario-validator-ui-kit",
    ":libfdx:tools:font",
    ":libfdx:extensions:graphics:gl:core",
    ":libfdx:extensions:graphics:gl:platform:desktop",
    ":libfdx:extensions:graphics:gl:platform:desktop_c",
    ":libfdx:extensions:graphics:gl:platform:web",
    ":libfdx:extensions:graphics:vulkan:core",
    ":libfdx:extensions:graphics:vulkan:platform:desktop",
    ":libfdx:extensions:graphics:vulkan:platform:desktop_c",
    ":libfdx:extensions:graphics:vulkan:platform:android_jni",
    ":libfdx:extensions:graphics:wgpu:core",
    ":libfdx:extensions:graphics:wgpu:platform:desktop_jni",
    ":libfdx:extensions:graphics:wgpu:platform:desktop_ffm",
    ":libfdx:extensions:graphics:wgpu:platform:android_jni",
    ":libfdx:extensions:graphics:wgpu:platform:web",
    ":libfdx:backends:desktop",
    ":libfdx:backends:desktop_c",
    ":libfdx:backends:ios_c",
    ":libfdx:backends:psp",
    ":libfdx:backends:android",
    ":libfdx:backends:web",
    ":libfdx:backends:c_shared"
)

fun Project.libfdxPublishableProjects(): List<Project> {
    return libfdxPublishableProjectPaths.map { path ->
        rootProject.findProject(path)
            ?: throw GradleException("Missing libFDX publishable project '$path'. Update libfdxPublishableProjectPaths in publish.gradle.kts.")
    }
}

fun isTaskRequested(taskName: String): Boolean {
    return taskNames.any { it == taskName || it.endsWith(":$taskName") }
}

val isPrepareSnapshotDeploy = isTaskRequested("prepareSnapshotDeploy")
val isPrepareReleaseDeploy = isTaskRequested("prepareReleaseDeploy")
val isPublishSnapshot = isTaskRequested("publishSnapshot")
val isPublishRelease = isTaskRequested("publishRelease")
val isUploadSnapshotDeploy = isTaskRequested("uploadSnapshotDeploy")
val isUploadToMavenCentral = isTaskRequested("uploadToMavenCentral")
val isZipStagingDeploy = isTaskRequested("zipStagingDeploy")
val isPublish = isTaskRequested("publish")
val isSnapshotLocalDeploy = isPrepareSnapshotDeploy || isPublishSnapshot
val isDeployPreparationTask = isSnapshotLocalDeploy || isPrepareReleaseDeploy || isZipStagingDeploy
val isReleaseLocalDeploy = isPrepareReleaseDeploy || isZipStagingDeploy
val isReleasePublishMode = isPrepareReleaseDeploy || isPublishRelease || isUploadToMavenCentral || isZipStagingDeploy
val isGradlePluginTarget = publishTarget == "GRADLE_PLUGIN"
val isSnapshotPublishMode = isSnapshotLocalDeploy || isUploadSnapshotDeploy || (isGradlePluginTarget && isPublish)
val libfdxVersion = if(isSnapshotPublishMode) "-SNAPSHOT" else libfdxBaseVersion

if(libfdxBaseVersion.endsWith("-SNAPSHOT")) {
    throw GradleException("The libFDX base version must not include -SNAPSHOT. Use the upcoming release version only.")
}

fun requiredEnvironment(name: String): String {
    return System.getenv(name)
        ?: throw GradleException("$name environment variable not set")
}

fun optionalEnvironment(vararg names: String): String? {
    return names.firstNotNullOfOrNull { name ->
        System.getenv(name)?.takeIf { it.isNotBlank() }
    }
}

fun releaseSigningKey(): String? {
    val value = optionalEnvironment("SIGNING_KEY", "PGP_SECRET") ?: return null
    val file = File(value)
    return if(file.isFile) {
        file.readText(Charsets.UTF_8)
    } else {
        value
    }
}

fun releaseSigningPassword(): String? {
    return optionalEnvironment("SIGNING_PASSWORD", "PGP_PASSPHRASE")
}

fun centralPublishingType(): String {
    val value = optionalEnvironment("CENTRAL_PUBLISHING_TYPE")
        ?.uppercase(Locale.ROOT)
        ?: "USER_MANAGED"
    if(value != "AUTOMATIC" && value != "USER_MANAGED") {
        throw GradleException("CENTRAL_PUBLISHING_TYPE must be AUTOMATIC or USER_MANAGED, got '$value'.")
    }
    return value
}

fun requireReleaseSigning(signingKey: String?, signingPassword: String?) {
    if(!isReleasePublishMode || libfdxVersion.endsWith("-SNAPSHOT")) {
        return
    }
    val missing = mutableListOf<String>()
    if(signingKey.isNullOrBlank()) {
        missing.add("SIGNING_KEY or PGP_SECRET")
    }
    if(signingPassword.isNullOrBlank()) {
        missing.add("SIGNING_PASSWORD or PGP_PASSPHRASE")
    }
    if(missing.isNotEmpty()) {
        throw GradleException("Release publishing requires signing credentials: ${missing.joinToString(", ")}.")
    }
}

fun Project.snapshotDeployDirectory(): File {
    return File(LibExt.rootDirectory, "build/snapshot-deploy")
}

fun Project.releaseStagingDirectory(): File {
    return File(LibExt.rootDirectory, "build/staging-deploy")
}

fun Project.releaseStagingZipFile(): File {
    return File(LibExt.rootDirectory, "build/staging-deploy.zip")
}

fun encodeMavenRepositoryPath(path: String): String {
    return path.split('/').joinToString("/") { segment ->
        URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
    }
}

fun snapshotDeployUploadPriority(relativePath: String): Int {
    return when {
        relativePath.endsWith("maven-metadata.xml") -> 1
        relativePath.contains("maven-metadata.xml.") -> 2
        else -> 0
    }
}

fun Project.uploadSnapshotDeployDirectory() {
    val deployDirectory = snapshotDeployDirectory()
    if(!deployDirectory.exists()) {
        throw GradleException("Snapshot deploy directory ${deployDirectory.absolutePath} does not exist. Run prepareSnapshotDeploy first.")
    }
    if(!deployDirectory.isDirectory) {
        throw GradleException("Snapshot deploy path ${deployDirectory.absolutePath} is not a directory.")
    }
    if(!Files.isReadable(Paths.get(deployDirectory.absolutePath))) {
        throw GradleException("Snapshot deploy directory ${deployDirectory.absolutePath} is not readable.")
    }

    val deployPath = deployDirectory.toPath()
    val files = deployDirectory.walkTopDown()
        .filter { it.isFile }
        .map { file ->
            val relativePath = deployPath.relativize(file.toPath()).toString().replace('\\', '/')
            relativePath to file
        }
        .sortedWith(
            compareBy<Pair<String, File>> { (relativePath, _) -> snapshotDeployUploadPriority(relativePath) }
                .thenBy { (relativePath, _) -> relativePath }
        )
        .toList()

    if(files.isEmpty()) {
        throw GradleException("Snapshot deploy directory ${deployDirectory.absolutePath} does not contain files to upload.")
    }

    val username = requiredEnvironment("CENTRAL_PORTAL_USERNAME")
    val password = requiredEnvironment("CENTRAL_PORTAL_PASSWORD")
    val repositoryBaseUrl = snapshotRepositoryUrl.trimEnd('/')
    files.forEach { (relativePath, file) ->
        val uploadUrl = "$repositoryBaseUrl/${encodeMavenRepositoryPath(relativePath)}"
        providers.exec {
            commandLine(
                "curl",
                "--fail",
                "--silent",
                "--show-error",
                "-u",
                "$username:$password",
                "--request",
                "PUT",
                "--upload-file",
                file.absolutePath,
                uploadUrl
            )
        }.result.get()
    }
    println("Uploaded ${files.size} snapshot deploy file(s) from ${deployDirectory.absolutePath}.")
}

fun isCentralReleaseArtifact(file: File): Boolean {
    if(!file.isFile) {
        return false
    }
    val name = file.name
    return name.endsWith(".jar")
        || name.endsWith(".aar")
        || name.endsWith(".pom")
        || name.endsWith(".module")
}

fun isPrimaryCentralReleaseArtifact(file: File): Boolean {
    if(!file.isFile) {
        return false
    }
    val name = file.name
    if(name.endsWith(".aar")) {
        return true
    }
    return name.endsWith(".jar")
        && !name.endsWith("-sources.jar")
        && !name.endsWith("-javadoc.jar")
}

fun Project.verifyReleaseStagingArtifacts() {
    val stagingDirectory = releaseStagingDirectory()
    if(!stagingDirectory.isDirectory) {
        throw GradleException("Release staging directory ${stagingDirectory.absolutePath} does not exist. Run prepareReleaseDeploy first.")
    }
    val artifacts = stagingDirectory.walkTopDown()
        .filter(::isCentralReleaseArtifact)
        .toList()
    if(artifacts.isEmpty()) {
        throw GradleException("Release staging directory ${stagingDirectory.absolutePath} does not contain Maven Central artifacts.")
    }
    val stagingPath = stagingDirectory.toPath()
    fun relativePath(file: File): String {
        return stagingPath.relativize(file.toPath()).toString().replace('\\', '/')
    }

    val errors = mutableListOf<String>()
    val missingSignatures = artifacts.filter { artifact ->
        !File("${artifact.absolutePath}.asc").isFile
    }
    if(missingSignatures.isNotEmpty()) {
        val listed = missingSignatures.take(40).joinToString(System.lineSeparator()) { artifact ->
            " - ${relativePath(artifact)}.asc"
        }
        val suffix = if(missingSignatures.size > 40) {
            "${System.lineSeparator()} - ... ${missingSignatures.size - 40} more missing signatures"
        } else {
            ""
        }
        errors.add("Release staging is missing ${missingSignatures.size} signature file(s):${System.lineSeparator()}$listed$suffix")
    }

    val missingSources = artifacts.groupBy { it.parentFile }
        .filter { (_, files) ->
            files.any(::isPrimaryCentralReleaseArtifact)
                && files.none { it.name.endsWith("-sources.jar") }
        }
        .keys
        .toList()
    if(missingSources.isNotEmpty()) {
        val listed = missingSources.take(40).joinToString(System.lineSeparator()) { directory ->
            " - ${relativePath(directory)}/*-sources.jar"
        }
        val suffix = if(missingSources.size > 40) {
            "${System.lineSeparator()} - ... ${missingSources.size - 40} more missing sources jars"
        } else {
            ""
        }
        errors.add("Release staging is missing sources jar(s) for ${missingSources.size} component(s):${System.lineSeparator()}$listed$suffix")
    }

    if(errors.isNotEmpty()) {
        throw GradleException(errors.joinToString("${System.lineSeparator()}${System.lineSeparator()}"))
    }
}

fun Project.uploadReleaseStagingZip() {
    val zipFile = releaseStagingZipFile()
    if(!zipFile.exists()) {
        throw GradleException("Release staging zip ${zipFile.absolutePath} does not exist. Run prepareReleaseDeploy first.")
    }
    if(!zipFile.isFile) {
        throw GradleException("Release staging zip ${zipFile.absolutePath} is not a file.")
    }
    if(!Files.isReadable(Paths.get(zipFile.absolutePath))) {
        throw GradleException("Release staging zip ${zipFile.absolutePath} is not readable.")
    }

    val username = requiredEnvironment("CENTRAL_PORTAL_USERNAME")
    val password = requiredEnvironment("CENTRAL_PORTAL_PASSWORD")
    val bundleName = URLEncoder.encode("$libfdxName-$libfdxVersion", "UTF-8")
    val publishingType = URLEncoder.encode(centralPublishingType(), "UTF-8")
    providers.exec {
        commandLine(
            "curl",
            "--fail",
            "--silent",
            "--show-error",
            "-u",
            "$username:$password",
            "--request",
            "POST",
            "--form",
            "bundle=@${zipFile.absolutePath}",
            "https://central.sonatype.com/api/v1/publisher/upload?name=$bundleName&publishingType=$publishingType"
        )
    }.result.get()
}

fun MavenPom.configureLibfdxPom(nameValue: String, descriptionValue: String) {
    name.set(nameValue)
    description.set(descriptionValue)
    url.set("https://github.com/libmdx/libfdx")
    developers {
        developer {
            id.set("Xpe")
            name.set("Natan")
        }
    }
    scm {
        connection.set("scm:git:git://github.com/libmdx/libfdx.git")
        developerConnection.set("scm:git:ssh://github.com/libmdx/libfdx.git")
        url.set("https://github.com/libmdx/libfdx")
    }
    licenses {
        license {
            name.set("The Apache License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
        }
    }
}

fun Project.publishArtifactId(): String {
    return extensions.findByType(BasePluginExtension::class.java)?.archivesName?.orNull
            ?: name.replace('-', '_')
}

fun Project.publishDescription(): String {
    return when {
        path.contains(":backends:") -> "libFDX backend module ${publishArtifactId()}"
        path.contains(":extensions:graphics:") -> "libFDX graphics provider module ${publishArtifactId()}"
        path.contains(":graphics:") -> "libFDX graphics module ${publishArtifactId()}"
        path.contains(":runtime:") -> "libFDX runtime module ${publishArtifactId()}"
        path.contains(":foundation:") -> "libFDX foundation module ${publishArtifactId()}"
        path.contains(":validation:") -> "libFDX validation module ${publishArtifactId()}"
        path.contains(":tools:") -> "libFDX tool module ${publishArtifactId()}"
        path.contains(":ui:") -> "libFDX UI module ${publishArtifactId()}"
        path.contains(":assets:") -> "libFDX asset module ${publishArtifactId()}"
        else -> "libFDX module ${publishArtifactId()}"
    }
}

fun runtimeFdxNativeValidationTaskPaths(): List<String> {
    return listOf(
        ":libfdx:runtime:fdx:platform:desktop:validate_runtime_fdx_desktop_c_resources",
        ":libfdx:runtime:fdx:platform:web:validate_runtime_fdx_web_native_resources"
    )
}

fun Project.androidReleaseAarFile(): File {
    return layout.buildDirectory.file("outputs/aar/${publishArtifactId()}-release.aar").get().asFile
}

fun Project.configurePomDependencies(pom: MavenPom, replaceExisting: Boolean) {
    pom.withXml {
        val projectNode = asNode()
        if(replaceExisting) {
            val existingDependencies = projectNode.children()
                .filterIsInstance<Node>()
                .filter { it.name().toString().substringAfterLast('}').substringAfterLast(':') == "dependencies" }
            existingDependencies.forEach { projectNode.remove(it) }
        }

        val dependenciesNode = projectNode.appendNode("dependencies")
        val seen = mutableSetOf<String>()

        fun addDependency(group: String, artifact: String, version: String, scope: String) {
            val key = "$group:$artifact"
            if(!seen.add(key)) {
                return
            }
            val dependencyNode = dependenciesNode.appendNode("dependency")
            dependencyNode.appendNode("groupId", group)
            dependencyNode.appendNode("artifactId", artifact)
            dependencyNode.appendNode("version", version)
            dependencyNode.appendNode("scope", scope)
        }

        fun addConfigurationDependencies(configurationName: String, scope: String) {
            configurations.findByName(configurationName)?.dependencies?.forEach { dependency: Dependency ->
                if(dependency is ProjectDependency) {
                    val dependencyProject = rootProject.findProject(dependency.path)
                        ?: throw GradleException("Could not resolve project dependency ${dependency.path} for ${project.path}")
                    addDependency(
                        libfdxGroup,
                        dependencyProject.publishArtifactId(),
                        libfdxVersion,
                        scope
                    )
                } else {
                    val group = dependency.group
                    val version = dependency.version
                    if(group != null && version != null) {
                        addDependency(group, dependency.name, version, scope)
                    }
                }
            }
        }

        addConfigurationDependencies("api", "compile")
        addConfigurationDependencies("implementation", "runtime")

        if(!dependenciesNode.children().isEmpty()) {
            return@withXml
        }
        projectNode.remove(dependenciesNode)
    }
}

fun Node.nodeLocalName(): String {
    return name().toString().substringAfterLast('}').substringAfterLast(':')
}

fun Node.childText(name: String): String? {
    return children()
        .filterIsInstance<Node>()
        .firstOrNull { it.nodeLocalName() == name }
        ?.text()
}

fun Project.gradlePluginDependencyArtifacts(): List<String> {
    if(!extensions.extraProperties.has(gradlePluginDependencyArtifactsProperty)) {
        return emptyList()
    }
    val value = extensions.extraProperties.get(gradlePluginDependencyArtifactsProperty)
    return when(value) {
        is Iterable<*> -> value.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
        is Array<*> -> value.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
        else -> listOf(value.toString()).filter(String::isNotBlank)
    }
}

fun Project.configureManualPomDependencies(pom: MavenPom) {
    configurePomDependencies(pom, replaceExisting = false)
}

fun Project.configureGradlePluginImplementationPomDependencies(pom: MavenPom) {
    configurePomDependencies(pom, replaceExisting = true)
    val dependencyArtifacts = gradlePluginDependencyArtifacts()
    if(dependencyArtifacts.isEmpty()) {
        return
    }
    pom.withXml {
        val projectNode = asNode()
        val dependenciesNode = projectNode.children()
            .filterIsInstance<Node>()
            .firstOrNull { it.nodeLocalName() == "dependencies" }
            ?: projectNode.appendNode("dependencies")
        val seen = dependenciesNode.children()
            .filterIsInstance<Node>()
            .mapNotNull { dependency ->
                val group = dependency.childText("groupId")
                val artifact = dependency.childText("artifactId")
                if(group == null || artifact == null) null else "$group:$artifact"
            }
            .toMutableSet()
        dependencyArtifacts.forEach { artifact ->
            if(!seen.add("$libfdxGroup:$artifact")) {
                return@forEach
            }
            val dependencyNode = dependenciesNode.appendNode("dependency")
            dependencyNode.appendNode("groupId", libfdxGroup)
            dependencyNode.appendNode("artifactId", artifact)
            dependencyNode.appendNode("version", libfdxVersion)
            dependencyNode.appendNode("scope", "runtime")
        }
    }
}

fun Project.configureLibfdxMavenRepository() {
    extensions.configure<PublishingExtension> {
        repositories {
            maven {
                name = "libfdxDeploy"
                url = when {
                    isSnapshotLocalDeploy -> uri(snapshotDeployDirectory())
                    isReleaseLocalDeploy -> uri(releaseStagingDirectory())
                    libfdxVersion.endsWith("-SNAPSHOT") -> uri(snapshotRepositoryUrl)
                    else -> uri(releaseStagingDirectory())
                }
                if(!isSnapshotLocalDeploy && !isReleaseLocalDeploy && libfdxVersion.endsWith("-SNAPSHOT")) {
                    credentials {
                        username = System.getenv("CENTRAL_PORTAL_USERNAME")
                        password = System.getenv("CENTRAL_PORTAL_PASSWORD")
                    }
                }
            }
        }
    }
}

fun Project.configureLibfdxSigning() {
    if(libfdxVersion.endsWith("-SNAPSHOT")) {
        return
    }
    val signingKey = releaseSigningKey()
    val signingPassword = releaseSigningPassword()
    requireReleaseSigning(signingKey, signingPassword)
    if(signingKey != null && signingPassword != null) {
        extensions.configure<SigningExtension> {
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(extensions.getByType(PublishingExtension::class.java).publications)
        }
    }
}

fun Project.configureLibfdxJavaPublishArtifacts(): TaskProvider<Jar> {
    tasks.withType(Javadoc::class.java).configureEach {
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    }
    extensions.configure<JavaPluginExtension> {
        withJavadocJar()
    }
    val javaExtension = extensions.getByType(JavaPluginExtension::class.java)
    return tasks.register("sourcesJar", Jar::class.java) {
        archiveClassifier.set("sources")
        from(javaExtension.sourceSets.named("main").get().allJava)
    }
}

fun Project.configureLibfdxLibraryPomMetadata() {
    extensions.configure<PublishingExtension> {
        publications.withType(MavenPublication::class.java).configureEach {
            val artifact = publishArtifactId()
            groupId = libfdxGroup
            artifactId = artifact
            version = libfdxVersion
            pom.configureLibfdxPom("libFDX $artifact", publishDescription())
        }
    }
}

fun Project.configureLibfdxGradlePluginPomMetadata() {
    extensions.configure<PublishingExtension> {
        publications.withType(MavenPublication::class.java).configureEach {
            groupId = libfdxGroup
            version = libfdxVersion
            pom.configureLibfdxPom(
                "libFDX Gradle plugin",
                "Gradle plugin for building libFDX web, desktop_c, PSP, and asset tasks."
            )
        }
    }
}

fun Project.configureJavaPublication() {
    apply(plugin = "maven-publish")
    apply(plugin = "signing")
    val sourcesJar = configureLibfdxJavaPublishArtifacts()
    extensions.configure<PublishingExtension> {
        publications.register("mavenJava", MavenPublication::class.java) {
            from(components.getByName("java"))
            artifact(sourcesJar)
        }
    }
    afterEvaluate {
        configureLibfdxLibraryPomMetadata()
    }
    configureLibfdxMavenRepository()
    configureLibfdxSigning()
}

fun Project.configureAndroidPublication() {
    apply(plugin = "maven-publish")
    apply(plugin = "signing")
    val androidJavadocJar = tasks.register("androidJavadocJar", Jar::class.java) {
        archiveClassifier.set("javadoc")
    }
    val androidSourcesJar = tasks.register("androidSourcesJar", Jar::class.java) {
        archiveClassifier.set("sources")
        from(layout.projectDirectory.dir("src/main/java"))
        from(layout.projectDirectory.dir("src/main/kotlin"))
    }
    val validateAndroidReleaseAar = tasks.register("validateAndroidReleaseAar") {
        group = "publishing"
        description = "Validates that the generated Android release AAR exists before deploy publication."
        doLast {
            val aarFile = androidReleaseAarFile()
            if(!aarFile.isFile) {
                throw GradleException("Missing generated Android release AAR ${aarFile.absolutePath}. Build the Android release artifact before running deploy preparation.")
            }
        }
    }
    afterEvaluate {
        extensions.configure<PublishingExtension> {
            publications.register("release", MavenPublication::class.java) {
                if(isDeployPreparationTask) {
                    artifact(androidReleaseAarFile()) {
                        extension = "aar"
                        builtBy(validateAndroidReleaseAar)
                    }
                    artifact(androidSourcesJar)
                    configureManualPomDependencies(pom)
                } else {
                    from(components.getByName("release"))
                }
                artifact(androidJavadocJar)
            }
        }
        configureLibfdxLibraryPomMetadata()
        configureLibfdxMavenRepository()
        configureLibfdxSigning()
    }
}

fun Project.configureGradlePluginPublishing() {
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    group = libfdxGroup
    version = libfdxVersion

    val sourcesJar = configureLibfdxJavaPublishArtifacts()
    extensions.configure<PublishingExtension> {
        publications.withType(MavenPublication::class.java).configureEach {
            if(name == "pluginMaven") {
                artifact(sourcesJar)
                configureGradlePluginImplementationPomDependencies(pom)
            }
        }
    }
    configureLibfdxMavenRepository()
    configureLibfdxGradlePluginPomMetadata()
    configureLibfdxSigning()

    tasks.register("prepareSnapshotDeploy") {
        group = "publishing"
        description = "Publish the libFDX Gradle plugin snapshot marker and implementation artifacts to a local repository."
        dependsOn(tasks.withType(PublishToMavenRepository::class.java))
        onlyIf { libfdxVersion.endsWith("-SNAPSHOT") }
    }

    tasks.register("prepareReleaseDeploy") {
        group = "publishing"
        description = "Publish the libFDX Gradle plugin release marker and implementation artifacts to a local repository."
        dependsOn(tasks.withType(PublishToMavenRepository::class.java))
        onlyIf { !libfdxVersion.endsWith("-SNAPSHOT") }
    }
}

fun Project.configureLibraryPublishing() {
    val publishableProjects = libfdxPublishableProjects()
    publishableProjects.forEach { publishProject ->
        publishProject.plugins.withId("java-library") {
            publishProject.configureJavaPublication()
        }
        publishProject.plugins.withId("com.android.library") {
            publishProject.configureAndroidPublication()
        }
    }

    tasks.register("listMavenDeployProjects") {
        group = "publishing"
        description = "Prints the explicit libFDX library projects included in Maven deploy."
        doLast {
            publishableProjects.forEach { project ->
                println("${project.path} -> ${project.publishArtifactId()}")
            }
        }
    }

    val libraryPublishTasks = publishableProjects.map { project ->
        project.tasks.withType(PublishToMavenRepository::class.java)
    }
    val gradlePluginBuildDir = layout.projectDirectory.dir("libfdx/tools/gradle-plugin").asFile
    val cleanSnapshotDeployDirectory = tasks.register("cleanSnapshotDeployDirectory") {
        group = "publishing"
        description = "Deletes the local snapshot deploy directory before preparing deploy artifacts."
        doLast {
            snapshotDeployDirectory().deleteRecursively()
        }
    }
    val cleanReleaseStagingDirectory = tasks.register("cleanReleaseStagingDirectory") {
        group = "publishing"
        description = "Deletes the local release staging deploy directory before preparing deploy artifacts."
        doLast {
            releaseStagingDirectory().deleteRecursively()
            releaseStagingZipFile().delete()
        }
    }
    libraryPublishTasks.forEach { publishTasks ->
        publishTasks.configureEach {
            mustRunAfter(cleanSnapshotDeployDirectory)
            mustRunAfter(cleanReleaseStagingDirectory)
        }
    }
    val validateRuntimeFdxNativeResources = tasks.register("validateRuntimeFdxNativeResources") {
        group = "publishing"
        description = "Validates generated runtime fdx native resources before deploy publication."
        dependsOn(runtimeFdxNativeValidationTaskPaths())
    }

    tasks.register<GradleBuild>("prepareGradlePluginSnapshotDeploy") {
        group = "publishing"
        description = "Prepare local snapshot deploy files for the libFDX Gradle plugin."
        dependsOn(validateRuntimeFdxNativeResources)
        dependsOn(libraryPublishTasks)
        mustRunAfter(cleanSnapshotDeployDirectory)
        dir = gradlePluginBuildDir
        tasks = listOf("prepareSnapshotDeploy")
    }

    tasks.register<GradleBuild>("prepareGradlePluginReleaseDeploy") {
        group = "publishing"
        description = "Prepare local release deploy files for the libFDX Gradle plugin."
        dependsOn(validateRuntimeFdxNativeResources)
        dependsOn(libraryPublishTasks)
        mustRunAfter(cleanReleaseStagingDirectory)
        dir = gradlePluginBuildDir
        tasks = listOf("prepareReleaseDeploy")
    }

    val verifyReleaseStagingArtifacts = tasks.register("verifyReleaseStagingArtifacts") {
        group = "publishing"
        description = "Validates that staged release artifacts have required sources jars and .asc signatures."
        dependsOn(libraryPublishTasks)
        dependsOn("prepareGradlePluginReleaseDeploy")
        mustRunAfter(cleanReleaseStagingDirectory)
        onlyIf { !libfdxVersion.endsWith("-SNAPSHOT") }
        doLast {
            verifyReleaseStagingArtifacts()
        }
    }

    tasks.register("prepareSnapshotDeploy") {
        group = "publishing"
        description = "Publish all libFDX snapshot artifacts to build/snapshot-deploy."
        dependsOn(cleanSnapshotDeployDirectory)
        dependsOn(validateRuntimeFdxNativeResources)
        dependsOn(libraryPublishTasks)
        dependsOn("prepareGradlePluginSnapshotDeploy")
        onlyIf { libfdxVersion.endsWith("-SNAPSHOT") }
    }

    tasks.register<Zip>("zipStagingDeploy") {
        group = "publishing"
        description = "Zip staged libFDX release artifacts for Central Portal upload."
        dependsOn(cleanReleaseStagingDirectory)
        dependsOn(validateRuntimeFdxNativeResources)
        dependsOn(verifyReleaseStagingArtifacts)
        from(releaseStagingDirectory())
        archiveFileName.set("staging-deploy.zip")
        destinationDirectory.set(releaseStagingZipFile().parentFile)
        onlyIf { !libfdxVersion.endsWith("-SNAPSHOT") }
    }

    tasks.register("prepareReleaseDeploy") {
        group = "publishing"
        description = "Publish all libFDX release artifacts to build/staging-deploy and create staging-deploy.zip."
        dependsOn("zipStagingDeploy")
        onlyIf { !libfdxVersion.endsWith("-SNAPSHOT") }
    }

    tasks.register<GradleBuild>("publishGradlePluginSnapshot") {
        group = "publishing"
        description = "Publish the libFDX Gradle plugin snapshot marker and implementation artifacts."
        dir = gradlePluginBuildDir
        tasks = listOf("publish")
    }

    tasks.register("publishSnapshot") {
        group = "publishing"
        description = "Prepare and upload build/snapshot-deploy to the Central Portal snapshot repository."
        dependsOn("prepareSnapshotDeploy")
        dependsOn("uploadSnapshotDeploy")
        onlyIf { libfdxVersion.endsWith("-SNAPSHOT") }
    }

    tasks.register("uploadSnapshotDeploy") {
        group = "publishing"
        description = "Upload build/snapshot-deploy to the Central Portal snapshot repository."
        mustRunAfter("prepareSnapshotDeploy")
        onlyIf { libfdxVersion.endsWith("-SNAPSHOT") }
        doLast {
            uploadSnapshotDeployDirectory()
        }
    }

    tasks.register("uploadToMavenCentral") {
        group = "publishing"
        description = "Upload build/staging-deploy.zip to Maven Central Portal."
        dependsOn("zipStagingDeploy")
        onlyIf { !libfdxVersion.endsWith("-SNAPSHOT") }
        doLast {
            uploadReleaseStagingZip()
        }
    }

    tasks.register("publishRelease") {
        group = "publishing"
        description = "Prepare libFDX release deploy files and upload them to Maven Central Portal."
        dependsOn("prepareReleaseDeploy")
        finalizedBy("uploadToMavenCentral")
        onlyIf { !libfdxVersion.endsWith("-SNAPSHOT") }
    }
}

when(publishTarget) {
    "LIBRARIES" -> configureLibraryPublishing()
    "GRADLE_PLUGIN" -> configureGradlePluginPublishing()
    else -> throw GradleException("$publishTargetProperty has unsupported value '$publishTarget'")
}
