import groovy.util.Node
import io.github.libfdx.build.LibExt
import java.io.File
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Locale
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension

// Constants and publishable module allowlist.
val libfdxName = "libfdx"
val snapshotRepositoryUrl = "https://central.sonatype.com/repository/maven-snapshots/"
val libfdxBaseVersion = LibExt.fdxVersion

val requestedTaskNames = gradle.startParameter.taskNames.map { it.substringAfterLast(":") }.toSet()
fun requested(vararg names: String): Boolean = names.any { it in requestedTaskNames }

val snapshotRequested = requested("prepareSnapshotDeploy", "publishSnapshot", "uploadSnapshotDeploy", "publishToMavenLocal") ||
    (plugins.hasPlugin("java-gradle-plugin") && requested("publish"))
val deployPreparationRequested = requested("prepareSnapshotDeploy", "prepareReleaseDeploy")
val libfdxVersion = if(snapshotRequested) LibExt.publishedLibfdxVersion else libfdxBaseVersion

if(libfdxBaseVersion.endsWith("-SNAPSHOT")) {
    throw GradleException("The libFDX base version must not include -SNAPSHOT. Use the upcoming release version only.")
}

val libfdxPublishableProjectPaths = listOf(
    ":libfdx:framework:math",
    ":libfdx:framework:json",
    ":libfdx:framework:collections",
    ":libfdx:framework:fdx:core",
    ":libfdx:framework:fdx:platform:shared",
    ":libfdx:framework:fdx:platform:desktop",
    ":libfdx:framework:fdx:platform:android",
    ":libfdx:framework:fdx:platform:web",
    ":libfdx:framework:application",
    ":libfdx:framework:display",
    ":libfdx:framework:files",
    ":libfdx:framework:input",
    ":libfdx:framework:net",
    ":libfdx:framework:storage",
    ":libfdx:framework:assets:manager",
    ":libfdx:framework:assets:loaders",
    ":libfdx:framework:graphics",
    ":libfdx:framework:camera",
    ":libfdx:framework:g2d",
    ":libfdx:framework:g3d",
    ":libfdx:framework:ui-kit",
    ":libfdx:extensions:scenario_validator:core",
    ":libfdx:extensions:scenario_validator:ui-kit",
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
    ":libfdx:extensions:net:webrtc:core",
    ":libfdx:extensions:net:webrtc:signaling_server",
    ":libfdx:extensions:net:webrtc:platform:desktop_jni",
    ":libfdx:extensions:net:webrtc:platform:web",
    ":libfdx:extensions:net:webrtc:platform:android_jni",
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

fun Project.hasLibfdxPublishableProjects(): Boolean {
    return libfdxPublishableProjectPaths.all { path -> rootProject.findProject(path) != null }
}

// Tiny helpers for paths, environment, and artifact ids.
fun Project.snapshotDeployDirectory(): File = File(LibExt.rootDirectory, "build/snapshot-deploy")
fun Project.releaseDeployDirectory(): File = File(LibExt.rootDirectory, "build/release-deploy")
fun Project.releaseDeployZipFile(): File = File(LibExt.rootDirectory, "build/release-deploy.zip")

fun optionalEnvironment(vararg names: String): String? {
    return names.firstNotNullOfOrNull { name -> System.getenv(name)?.takeIf { it.isNotBlank() } }
}

fun requiredEnvironment(name: String): String {
    return System.getenv(name) ?: throw GradleException("$name environment variable not set")
}

fun centralPublishingType(): String {
    val value = optionalEnvironment("CENTRAL_PUBLISHING_TYPE")?.uppercase(Locale.ROOT) ?: "USER_MANAGED"
    if(value != "AUTOMATIC" && value != "USER_MANAGED") {
        throw GradleException("CENTRAL_PUBLISHING_TYPE must be AUTOMATIC or USER_MANAGED, got '$value'.")
    }
    return value
}

fun Project.publishArtifactId(): String {
    return extensions.findByType(BasePluginExtension::class.java)?.archivesName?.orNull
        ?: name.replace('-', '_')
}

fun Project.isAndroidLibraryProject(): Boolean = plugins.hasPlugin("com.android.library")
fun Project.isGradlePluginProject(): Boolean = plugins.hasPlugin("java-gradle-plugin")

// Publication conventions shared by libraries and the Gradle plugin.
fun Project.applyPublishingConventions() {
    pluginManager.withPlugin("maven-publish") {
        configureMavenDeployRepository()
        configureJavadocOptions()

        afterEvaluate {
            val publishing = extensions.getByType<PublishingExtension>()
            val publications = publishing.publications.withType(MavenPublication::class.java)
            if(publications.isEmpty()) {
                throw GradleException("$path must declare at least one MavenPublication in its own build.gradle.kts.")
            }

            publications.configureEach {
                configureMavenPublication(this@applyPublishingConventions, this)
            }
            if(deployPreparationRequested) {
                addNoBuildDeployPublications(publishing)
            }
        }
    }

    afterEvaluate {
        if(!plugins.hasPlugin("maven-publish")) {
            throw GradleException("$path must apply 'maven-publish' in its own plugins block before applying publish.gradle.kts.")
        }
    }
}

fun Project.configureMavenDeployRepository() {
    extensions.configure<PublishingExtension> {
        repositories {
            maven {
                name = "libfdxDeploy"
                url = when {
                    deployPreparationRequested && libfdxVersion.endsWith("-SNAPSHOT") -> uri(snapshotDeployDirectory())
                    deployPreparationRequested -> uri(releaseDeployDirectory())
                    libfdxVersion.endsWith("-SNAPSHOT") -> uri(snapshotRepositoryUrl)
                    else -> uri(releaseDeployDirectory())
                }
                if(!deployPreparationRequested && libfdxVersion.endsWith("-SNAPSHOT")) {
                    credentials {
                        username = System.getenv("CENTRAL_PORTAL_USERNAME")
                        password = System.getenv("CENTRAL_PORTAL_PASSWORD")
                    }
                }
            }
        }
    }
}

fun Project.configureJavadocOptions() {
    tasks.withType(Javadoc::class.java).configureEach {
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    }
}

fun MavenPublication.configureMavenPublication(owner: Project, publication: MavenPublication) {
    groupId = LibExt.fdxGroup
    version = libfdxVersion
    if(artifactId.isBlank()) {
        artifactId = owner.publishArtifactId()
    }

    pom.configureLibfdxPom(owner, artifactId)
    when {
        owner.isGradlePluginProject() && artifactId == "io.github.libfdx.gradle.plugin" -> {
            pom.configureGradlePluginMarkerPom()
        }
        owner.isGradlePluginProject() -> {
            owner.configureGradlePluginPomDependencies(pom)
        }
        else -> {
            owner.configurePomDependencies(pom)
        }
    }

}

// Deploy preparation uses deploy-only publications so no compile/jar tasks enter the graph.
fun Project.addNoBuildDeployPublications(publishing: PublishingExtension) {
    if(isGradlePluginProject()) {
        if(publishing.publications.findByName("deployPluginMaven") == null) {
            publishing.publications.create("deployPluginMaven", MavenPublication::class.java) {
                groupId = LibExt.fdxGroup
                artifactId = "gradle-plugin"
                version = libfdxVersion
                pom.configureLibfdxPom(this@addNoBuildDeployPublications, artifactId)
                configureGradlePluginPomDependencies(pom)
                configureDeployArtifacts(this)
            }
        }
        if(publishing.publications.findByName("deployLibfdxPluginMarkerMaven") == null) {
            publishing.publications.create("deployLibfdxPluginMarkerMaven", MavenPublication::class.java) {
                groupId = LibExt.fdxGroup
                artifactId = "io.github.libfdx.gradle.plugin"
                version = libfdxVersion
                pom.configureLibfdxPom(this@addNoBuildDeployPublications, artifactId)
                pom.configureGradlePluginMarkerPom()
            }
        }
        return
    }

    if(publishing.publications.findByName("deployMaven") == null) {
        publishing.publications.create("deployMaven", MavenPublication::class.java) {
            groupId = LibExt.fdxGroup
            artifactId = publishArtifactId()
            version = libfdxVersion
            pom.configureLibfdxPom(this@addNoBuildDeployPublications, artifactId)
            configurePomDependencies(pom)
            configureDeployArtifacts(this)
        }
    }
}

fun MavenPom.configureLibfdxPom(owner: Project, artifactId: String) {
    val isMarker = artifactId == "io.github.libfdx.gradle.plugin"
    name.set(
        when {
            owner.isGradlePluginProject() && isMarker -> "libFDX Gradle plugin marker"
            owner.isGradlePluginProject() -> "libFDX Gradle plugin"
            else -> "libFDX $artifactId"
        }
    )
    description.set(
        when {
            owner.isGradlePluginProject() && isMarker -> "Gradle plugin marker for io.github.libfdx."
            owner.isGradlePluginProject() -> "Gradle plugin for building libFDX web, desktop_c, PSP, and asset tasks."
            else -> owner.publishDescription(artifactId)
        }
    )
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

fun Project.publishDescription(artifactId: String): String {
    return when {
        path.contains(":backends:") -> "libFDX backend module $artifactId"
        path.contains(":extensions:graphics:") -> "libFDX graphics provider module $artifactId"
        path == ":libfdx:framework:graphics" ||
            path == ":libfdx:framework:camera" ||
            path == ":libfdx:framework:g2d" ||
            path == ":libfdx:framework:g3d" -> "libFDX graphics module $artifactId"
        path == ":libfdx:framework:fdx:core" ||
            path == ":libfdx:framework:fdx:platform:shared" ||
            path == ":libfdx:framework:fdx:platform:desktop" ||
            path == ":libfdx:framework:fdx:platform:android" ||
            path == ":libfdx:framework:fdx:platform:web" ||
            path == ":libfdx:framework:application" ||
            path == ":libfdx:framework:display" ||
            path == ":libfdx:framework:files" ||
            path == ":libfdx:framework:input" ||
            path == ":libfdx:framework:net" ||
            path == ":libfdx:framework:storage" -> "libFDX runtime module $artifactId"
        path == ":libfdx:framework:math" ||
            path == ":libfdx:framework:json" ||
            path == ":libfdx:framework:collections" -> "libFDX foundation module $artifactId"
        path.contains(":scenario_validator:") -> "libFDX scenario validator module $artifactId"
        path.contains(":tools:") -> "libFDX tool module $artifactId"
        path == ":libfdx:framework:ui-kit" -> "libFDX UI module $artifactId"
        path.contains(":framework:assets:") -> "libFDX asset module $artifactId"
        else -> "libFDX module $artifactId"
    }
}

fun Project.configurePomDependencies(pom: MavenPom) {
    pom.withXml {
        val projectNode = asNode()
        projectNode.removeDependencyNodes()
        val dependenciesNode = projectNode.appendNode("dependencies")
        val seen = mutableSetOf<String>()

        fun addDependency(
            group: String,
            artifact: String,
            version: String,
            scope: String,
            classifier: String? = null,
            type: String? = null
        ) {
            if(!seen.add("$group:$artifact:$version:$scope:${classifier.orEmpty()}:${type.orEmpty()}")) {
                return
            }
            val dependencyNode = dependenciesNode.appendNode("dependency")
            dependencyNode.appendNode("groupId", group)
            dependencyNode.appendNode("artifactId", artifact)
            dependencyNode.appendNode("version", version)
            classifier?.let { dependencyNode.appendNode("classifier", it) }
            type?.let { dependencyNode.appendNode("type", it) }
            dependencyNode.appendNode("scope", scope)
        }

        fun addConfigurationDependencies(configurationName: String, scope: String) {
            configurations.findByName(configurationName)?.dependencies?.forEach { dependency: Dependency ->
                if(dependency is ProjectDependency) {
                    val dependencyProject = rootProject.findProject(dependency.path)
                        ?: throw GradleException("Could not resolve project dependency ${dependency.path} for ${project.path}")
                    addDependency(LibExt.fdxGroup, dependencyProject.publishArtifactId(), libfdxVersion, scope)
                } else {
                    val group = dependency.group
                    val version = dependency.version
                    if(group != null && version != null) {
                        val artifacts = (dependency as? ModuleDependency)?.artifacts.orEmpty()
                        if(artifacts.isEmpty()) {
                            addDependency(group, dependency.name, version, scope)
                        } else {
                            artifacts.forEach { artifact ->
                                val classifier = artifact.classifier?.takeIf(String::isNotBlank)
                                val type = artifact.type.takeIf { it.isNotBlank() && it != "jar" }
                                addDependency(group, dependency.name, version, scope, classifier, type)
                            }
                        }
                    }
                }
            }
        }

        addConfigurationDependencies("api", "compile")
        addConfigurationDependencies("implementation", "runtime")
        addConfigurationDependencies("runtimeOnly", "runtime")

        if(dependenciesNode.children().isEmpty()) {
            projectNode.remove(dependenciesNode)
        }
    }
}

fun Project.configureGradlePluginPomDependencies(pom: MavenPom) {
    pom.withXml {
        val projectNode = asNode()
        projectNode.removeDependencyNodes()
        val dependenciesNode = projectNode.appendNode("dependencies")
        val seen = mutableSetOf<String>()

        fun addDependency(group: String, artifact: String, version: String, scope: String) {
            if(!seen.add("$group:$artifact:$scope")) {
                return
            }
            val dependencyNode = dependenciesNode.appendNode("dependency")
            dependencyNode.appendNode("groupId", group)
            dependencyNode.appendNode("artifactId", artifact)
            dependencyNode.appendNode("version", version)
            dependencyNode.appendNode("scope", scope)
        }

        configurations.findByName("implementation")?.dependencies?.forEach { dependency ->
            val group = dependency.group
            val version = dependency.version
            if(group != null && version != null && group != LibExt.fdxGroup) {
                addDependency(group, dependency.name, version, "runtime")
            }
        }

        gradlePluginDependencyArtifacts().forEach { artifact ->
            addDependency(LibExt.fdxGroup, artifact, libfdxVersion, "runtime")
        }

        if(dependenciesNode.children().isEmpty()) {
            projectNode.remove(dependenciesNode)
        }
    }
}

fun MavenPom.configureGradlePluginMarkerPom() {
    withXml {
        val projectNode = asNode()
        projectNode.removeDependencyNodes()
        val dependenciesNode = projectNode.appendNode("dependencies")
        val dependencyNode = dependenciesNode.appendNode("dependency")
        dependencyNode.appendNode("groupId", LibExt.fdxGroup)
        dependencyNode.appendNode("artifactId", "gradle-plugin")
        dependencyNode.appendNode("version", libfdxVersion)
    }
}

fun Project.gradlePluginDependencyArtifacts(): List<String> {
    if(!extensions.extraProperties.has("libfdxGradlePluginDependencyArtifacts")) {
        return emptyList()
    }
    val value = extensions.extraProperties.get("libfdxGradlePluginDependencyArtifacts")
    return when(value) {
        is Iterable<*> -> value.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
        is Array<*> -> value.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
        else -> listOf(value.toString()).filter(String::isNotBlank)
    }
}

fun Project.configureDeployArtifacts(publication: MavenPublication) {
    if(isGradlePluginProject() && publication.artifactId == "io.github.libfdx.gradle.plugin") {
        return
    }

    val files = deployArtifactFiles(publication.artifactId)
    val validateArtifacts = validateExistingDeployArtifactsTask(publication.artifactId, files)
    val artifactBuildTasks = files.flatMap { it.buildTasks }.distinct()
    val artifactBuildTaskCollections = artifactBuildTasks.map { taskName -> tasks.matching { it.name == taskName } }
    publication.setArtifacts(emptyList<Any>())
    files.forEach { artifact ->
        publication.artifact(artifact.file) {
            artifact.classifier?.let { classifier = it }
            artifact.extension?.let { extension = it }
            artifact.buildTasks.forEach { taskName -> builtBy(tasks.matching { it.name == taskName }) }
            builtBy(validateArtifacts)
        }
    }
    tasks.matching { it.name == validateArtifacts.name }.configureEach {
        artifactBuildTaskCollections.forEach { dependsOn(it) }
    }
}

data class DeployArtifact(
    val file: File,
    val classifier: String? = null,
    val extension: String? = null,
    val buildTasks: List<String> = emptyList()
)

fun Project.deployArtifactFiles(artifactId: String): List<DeployArtifact> {
    return if(isAndroidLibraryProject()) {
        listOf(
            DeployArtifact(layout.buildDirectory.file("outputs/aar/$artifactId-release.aar").get().asFile, extension = "aar"),
            DeployArtifact(layout.buildDirectory.file("libs/$artifactId-${LibExt.fdxVersion}-sources.jar").get().asFile, classifier = "sources", buildTasks = listOf("androidSourcesJar")),
            DeployArtifact(layout.buildDirectory.file("libs/$artifactId-${LibExt.fdxVersion}-javadoc.jar").get().asFile, classifier = "javadoc", buildTasks = listOf("androidJavadocJar"))
        )
    } else {
        listOf(
            DeployArtifact(layout.buildDirectory.file("libs/$artifactId-${LibExt.fdxVersion}.jar").get().asFile, buildTasks = listOf("jar")),
            DeployArtifact(layout.buildDirectory.file("libs/$artifactId-${LibExt.fdxVersion}-sources.jar").get().asFile, classifier = "sources", buildTasks = listOf("sourcesJar")),
            DeployArtifact(layout.buildDirectory.file("libs/$artifactId-${LibExt.fdxVersion}-javadoc.jar").get().asFile, classifier = "javadoc", buildTasks = listOf("javadocJar"))
        )
    }
}

fun Project.validateExistingDeployArtifactsTask(
    artifactId: String,
    artifacts: List<DeployArtifact>
): TaskProvider<Task> {
    val taskName = "validate${artifactId.toTaskNamePart()}DeployArtifacts"
    return tasks.register(taskName) {
        group = "publishing"
        description = "Validates that existing deploy artifacts for $artifactId are present."
        doLast {
            val missing = artifacts.map { it.file }.filterNot { it.isFile }
            if(missing.isNotEmpty()) {
                val listed = missing.joinToString(System.lineSeparator()) { " - ${it.absolutePath}" }
                throw GradleException(
                    "Deploy preparation builds Java deploy artifacts and stages existing native/Android artifacts." +
                        "${System.lineSeparator()}Missing artifact file(s):" +
                        "${System.lineSeparator()}$listed" +
                        "${System.lineSeparator()}Build or download required native/Android artifacts before deploy preparation."
                )
            }
        }
    }
}

fun String.toTaskNamePart(): String {
    return split('_', '-')
        .filter(String::isNotBlank)
        .joinToString("") { part -> part.replaceFirstChar { it.uppercase(Locale.ROOT) } }
}

fun Project.configureReleaseSigningCredentials() {
    pluginManager.apply("signing")
    extensions.configure<SigningExtension> {
        val signingKey = releaseSigningKey()
        val signingPassword = releaseSigningPassword()
        if(signingKey != null && signingPassword != null) {
            useInMemoryPgpKeys(signingKey, signingPassword)
        }
    }
}

fun releaseSigningKey(): String? {
    val value = optionalEnvironment("SIGNING_KEY", "PGP_SECRET") ?: return null
    val file = File(value)
    return if(file.isFile) file.readText(Charsets.UTF_8) else value
}

fun releaseSigningPassword(): String? {
    return optionalEnvironment("SIGNING_PASSWORD", "PGP_PASSPHRASE")
}

fun runtimeFdxNativeValidationTaskPaths(): List<String> {
    return listOf(
        ":libfdx:framework:fdx:platform:desktop:validate_runtime_fdx_desktop_c_resources",
        ":libfdx:framework:fdx:platform:web:validate_runtime_fdx_web_native_resources"
    )
}

fun isCentralReleaseArtifact(file: File): Boolean {
    val name = file.name
    return file.isFile && (name.endsWith(".jar") || name.endsWith(".aar") || name.endsWith(".pom") || name.endsWith(".module"))
}

fun isPrimaryCentralReleaseArtifact(file: File): Boolean {
    val name = file.name
    return file.isFile && (name.endsWith(".aar") || (name.endsWith(".jar") &&
        !name.endsWith("-sources.jar") && !name.endsWith("-javadoc.jar")))
}

fun Project.releaseDeployArtifacts(): List<File> {
    val releaseDirectory = releaseDeployDirectory()
    if(!releaseDirectory.isDirectory) {
        return emptyList()
    }
    return releaseDirectory.walkTopDown().filter(::isCentralReleaseArtifact).toList()
}

fun Project.verifyReleaseDeployArtifacts(requireSignatures: Boolean) {
    val releaseDirectory = releaseDeployDirectory()
    if(!releaseDirectory.isDirectory) {
        throw GradleException("Release deploy directory ${releaseDirectory.absolutePath} does not exist. Run prepareReleaseDeploy first.")
    }
    val artifacts = releaseDeployArtifacts()
    if(artifacts.isEmpty()) {
        throw GradleException("Release deploy directory ${releaseDirectory.absolutePath} does not contain Maven Central artifacts.")
    }

    val releasePath = releaseDirectory.toPath()
    fun relativePath(file: File): String = releasePath.relativize(file.toPath()).toString().replace('\\', '/')
    val errors = mutableListOf<String>()

    val missingSignatures = if(requireSignatures) artifacts.filter { !File("${it.absolutePath}.asc").isFile } else emptyList()
    if(missingSignatures.isNotEmpty()) {
        val listed = missingSignatures.take(40).joinToString(System.lineSeparator()) { " - ${relativePath(it)}.asc" }
        val suffix = if(missingSignatures.size > 40) {
            "${System.lineSeparator()} - ... ${missingSignatures.size - 40} more missing signatures"
        } else {
            ""
        }
        errors.add("Release deploy is missing ${missingSignatures.size} signature file(s):${System.lineSeparator()}$listed$suffix")
    }

    val missingSources = artifacts.groupBy { it.parentFile }
        .filter { (_, files) -> files.any(::isPrimaryCentralReleaseArtifact) && files.none { it.name.endsWith("-sources.jar") } }
        .keys
        .toList()
    if(missingSources.isNotEmpty()) {
        val listed = missingSources.take(40).joinToString(System.lineSeparator()) { " - ${relativePath(it)}/*-sources.jar" }
        val suffix = if(missingSources.size > 40) {
            "${System.lineSeparator()} - ... ${missingSources.size - 40} more missing sources jars"
        } else {
            ""
        }
        errors.add("Release deploy is missing sources jar(s) for ${missingSources.size} component(s):${System.lineSeparator()}$listed$suffix")
    }

    if(errors.isNotEmpty()) {
        throw GradleException(errors.joinToString("${System.lineSeparator()}${System.lineSeparator()}"))
    }
}

fun encodeMavenRepositoryPath(path: String): String {
    return path.split('/').joinToString("/") { segment -> URLEncoder.encode(segment, "UTF-8").replace("+", "%20") }
}

// Upload tasks operate only on existing deploy files.
fun snapshotDeployUploadPriority(relativePath: String): Int {
    return when {
        relativePath.endsWith("maven-metadata.xml") -> 1
        relativePath.contains("maven-metadata.xml.") -> 2
        else -> 0
    }
}

fun Project.uploadSnapshotDeployDirectory() {
    val deployDirectory = snapshotDeployDirectory()
    if(!deployDirectory.isDirectory) {
        throw GradleException("Snapshot deploy directory ${deployDirectory.absolutePath} does not exist. Run prepareSnapshotDeploy first.")
    }
    if(!Files.isReadable(Paths.get(deployDirectory.absolutePath))) {
        throw GradleException("Snapshot deploy directory ${deployDirectory.absolutePath} is not readable.")
    }
    val deployPath = deployDirectory.toPath()
    val files = deployDirectory.walkTopDown()
        .filter { it.isFile }
        .map { file -> deployPath.relativize(file.toPath()).toString().replace('\\', '/') to file }
        .sortedWith(compareBy<Pair<String, File>> { snapshotDeployUploadPriority(it.first) }.thenBy { it.first })
        .toList()
    if(files.isEmpty()) {
        throw GradleException("Snapshot deploy directory ${deployDirectory.absolutePath} does not contain files to upload.")
    }

    val username = requiredEnvironment("CENTRAL_PORTAL_USERNAME")
    val password = requiredEnvironment("CENTRAL_PORTAL_PASSWORD")
    val repositoryBaseUrl = snapshotRepositoryUrl.trimEnd('/')
    files.forEach { (relativePath, file) ->
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
                "$repositoryBaseUrl/${encodeMavenRepositoryPath(relativePath)}"
            )
        }.result.get()
    }
    println("Uploaded ${files.size} snapshot deploy file(s) from ${deployDirectory.absolutePath}.")
}

fun Project.uploadReleaseDeployZip() {
    val zipFile = releaseDeployZipFile()
    if(!zipFile.isFile) {
        throw GradleException("Release deploy zip ${zipFile.absolutePath} does not exist. Run prepareReleaseDeploy first.")
    }
    if(!Files.isReadable(Paths.get(zipFile.absolutePath))) {
        throw GradleException("Release deploy zip ${zipFile.absolutePath} is not readable.")
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

fun Project.configureUploadTasks() {
    configureReleaseSigningCredentials()

    val signReleaseDeploy = tasks.register<Sign>("signReleaseDeploy") {
        group = "publishing"
        description = "Signs existing build/release-deploy Maven Central artifacts."
        onlyIf { !libfdxVersion.endsWith("-SNAPSHOT") }
        doFirst {
            val signingKey = releaseSigningKey()
            val signingPassword = releaseSigningPassword()
            if(signingKey == null || signingPassword == null) {
                throw GradleException(
                    "publishRelease requires release signing credentials because it signs existing build/release-deploy files. " +
                        "Set SIGNING_KEY or PGP_SECRET, and SIGNING_PASSWORD or PGP_PASSPHRASE."
                )
            }
            val artifacts = releaseDeployArtifacts()
            if(artifacts.isEmpty()) {
                throw GradleException("Release deploy directory ${releaseDeployDirectory().absolutePath} does not contain Maven Central artifacts. Run prepareReleaseDeploy first.")
            }
            sign(*artifacts.toTypedArray())
        }
    }
    val verifyReleaseDeployArtifacts = tasks.register("verifyReleaseDeployArtifacts") {
        group = "publishing"
        description = "Validates that release deploy artifacts have required sources jars and .asc signatures."
        dependsOn(signReleaseDeploy)
        doLast { verifyReleaseDeployArtifacts(requireSignatures = true) }
    }
    val zipReleaseDeploy = tasks.register<Zip>("zipReleaseDeploy") {
        group = "publishing"
        description = "Zips signed build/release-deploy files as build/release-deploy.zip."
        dependsOn(verifyReleaseDeployArtifacts)
        from(releaseDeployDirectory())
        archiveFileName.set("release-deploy.zip")
        destinationDirectory.set(releaseDeployZipFile().parentFile)
    }
    tasks.register("uploadSnapshotDeploy") {
        group = "publishing"
        description = "Uploads existing build/snapshot-deploy files to the Central Portal snapshot repository."
        mustRunAfter("prepareSnapshotDeploy")
        doLast { uploadSnapshotDeployDirectory() }
    }
    tasks.register("uploadReleaseDeploy") {
        group = "publishing"
        description = "Uploads existing build/release-deploy.zip to Maven Central Portal."
        dependsOn(zipReleaseDeploy)
        mustRunAfter("prepareReleaseDeploy")
        doLast { uploadReleaseDeployZip() }
    }
    tasks.register("publishSnapshot") {
        group = "publishing"
        description = "Uploads existing build/snapshot-deploy files to the Central Portal snapshot repository."
        dependsOn("uploadSnapshotDeploy")
        onlyIf { libfdxVersion.endsWith("-SNAPSHOT") }
    }
    tasks.register("publishRelease") {
        group = "publishing"
        description = "Signs, zips, and uploads existing build/release-deploy files to Maven Central Portal."
        dependsOn("uploadReleaseDeploy")
        onlyIf { !libfdxVersion.endsWith("-SNAPSHOT") }
    }
}

fun Project.configureGradlePluginDeploy() {
    configureUploadTasks()
    applyPublishingConventions()

    val pluginPublishTasks = listOf(
        "publishDeployLibfdxPluginMarkerMavenPublicationToLibfdxDeployRepository",
        "publishDeployPluginMavenPublicationToLibfdxDeployRepository"
    )
    val verifyPreparedReleaseDeployArtifacts = tasks.register("verifyPreparedReleaseDeployArtifacts") {
        group = "publishing"
        description = "Validates that prepared release deploy artifacts have required sources jars."
        dependsOn(pluginPublishTasks)
        doLast { verifyReleaseDeployArtifacts(requireSignatures = false) }
    }
    tasks.register("prepareSnapshotDeploy") {
        group = "publishing"
        description = "Prepares libFDX Gradle plugin snapshot artifacts in build/snapshot-deploy."
        dependsOn(pluginPublishTasks)
        onlyIf { libfdxVersion.endsWith("-SNAPSHOT") }
    }
    tasks.register("prepareReleaseDeploy") {
        group = "publishing"
        description = "Prepares libFDX Gradle plugin release artifacts in build/release-deploy."
        dependsOn(pluginPublishTasks, verifyPreparedReleaseDeployArtifacts)
        onlyIf { !libfdxVersion.endsWith("-SNAPSHOT") }
    }
}

fun Project.configureRootLibraryDeploy() {
    configureUploadTasks()

    val publishableProjects = libfdxPublishableProjects()
    publishableProjects.forEach { publishProject ->
        publishProject.applyPublishingConventions()
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

    val libraryPublishTasks = publishableProjects.map { "${it.path}:publishDeployMavenPublicationToLibfdxDeployRepository" }
    val gradlePluginBuildDir = layout.projectDirectory.dir("libfdx/tools/gradle-plugin").asFile
    val cleanSnapshotDeployDirectory = tasks.register("cleanSnapshotDeployDirectory") {
        group = "publishing"
        description = "Deletes build/snapshot-deploy."
        doLast { snapshotDeployDirectory().deleteRecursively() }
    }
    val cleanReleaseDeployDirectory = tasks.register("cleanReleaseDeployDirectory") {
        group = "publishing"
        description = "Deletes build/release-deploy and build/release-deploy.zip."
        doLast {
            releaseDeployDirectory().deleteRecursively()
            releaseDeployZipFile().delete()
        }
    }
    val validateRuntimeFdxNativeResources = tasks.register("validateRuntimeFdxNativeResources") {
        group = "publishing"
        description = "Validates generated runtime fdx native resources before deploy publication."
        dependsOn(runtimeFdxNativeValidationTaskPaths())
    }

    publishableProjects.forEach { project ->
        project.tasks.matching { it.name == "publishDeployMavenPublicationToLibfdxDeployRepository" }.configureEach {
            mustRunAfter(cleanSnapshotDeployDirectory, cleanReleaseDeployDirectory)
        }
    }

    val prepareGradlePluginSnapshotDeploy = tasks.register<GradleBuild>("prepareGradlePluginSnapshotDeploy") {
        group = "publishing"
        description = "Prepares Gradle plugin snapshot artifacts in build/snapshot-deploy."
        dependsOn(validateRuntimeFdxNativeResources, libraryPublishTasks)
        mustRunAfter(cleanSnapshotDeployDirectory)
        dir = gradlePluginBuildDir
        tasks = listOf("prepareSnapshotDeploy")
    }
    val prepareGradlePluginReleaseDeploy = tasks.register<GradleBuild>("prepareGradlePluginReleaseDeploy") {
        group = "publishing"
        description = "Prepares Gradle plugin release artifacts in build/release-deploy."
        dependsOn(validateRuntimeFdxNativeResources, libraryPublishTasks)
        mustRunAfter(cleanReleaseDeployDirectory)
        dir = gradlePluginBuildDir
        tasks = listOf("prepareReleaseDeploy")
    }
    val verifyPreparedReleaseDeployArtifacts = tasks.register("verifyPreparedReleaseDeployArtifacts") {
        group = "publishing"
        description = "Validates that prepared release deploy artifacts have required sources jars."
        dependsOn(libraryPublishTasks, prepareGradlePluginReleaseDeploy)
        mustRunAfter(cleanReleaseDeployDirectory)
        doLast { verifyReleaseDeployArtifacts(requireSignatures = false) }
    }

    tasks.register("prepareSnapshotDeploy") {
        group = "publishing"
        description = "Prepares snapshot deploy files in build/snapshot-deploy."
        dependsOn(cleanSnapshotDeployDirectory, validateRuntimeFdxNativeResources, libraryPublishTasks, prepareGradlePluginSnapshotDeploy)
        onlyIf { libfdxVersion.endsWith("-SNAPSHOT") }
    }
    tasks.register("prepareReleaseDeploy") {
        group = "publishing"
        description = "Prepares unsigned release deploy files in build/release-deploy."
        dependsOn(cleanReleaseDeployDirectory, validateRuntimeFdxNativeResources, libraryPublishTasks, prepareGradlePluginReleaseDeploy, verifyPreparedReleaseDeployArtifacts)
        onlyIf { !libfdxVersion.endsWith("-SNAPSHOT") }
    }
}

fun Node.removeDependencyNodes() {
    children()
        .filterIsInstance<Node>()
        .filter { it.nodeLocalName() == "dependencies" }
        .forEach { remove(it) }
}

fun Node.nodeLocalName(): String {
    return name().toString().substringAfterLast('}').substringAfterLast(':')
}

when {
    plugins.hasPlugin("java-gradle-plugin") -> configureGradlePluginDeploy()
    hasLibfdxPublishableProjects() -> configureRootLibraryDeploy()
    else -> throw GradleException("publish.gradle.kts must be applied to the libFDX root build or the libFDX Gradle plugin build.")
}
