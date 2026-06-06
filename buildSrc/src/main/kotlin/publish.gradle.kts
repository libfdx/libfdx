import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Paths
import org.gradle.api.Project
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

val libfdxName = "libfdx"
val snapshotRepositoryUrl = "https://central.sonatype.com/repository/maven-snapshots/"
val taskNames = gradle.startParameter.taskNames
val requestedTaskBaseNames = taskNames.map { it.substringAfterLast(':') }
fun Project.findLibfdxTomlFile(): File? {
    var directory: File? = rootProject.projectDir
    while(directory != null) {
        val candidate = File(directory, "libfdx.toml")
        if(candidate.isFile) {
            return candidate
        }
        directory = directory.parentFile
    }
    return null
}

fun readLibfdxTomlValue(file: File?, section: String, key: String): String? {
    if(file == null || !file.isFile) {
        return null
    }
    var inTargetSection = false
    var value: String? = null
    file.useLines { lines ->
        for(rawLine in lines) {
            val line = rawLine.substringBefore("#").trim()
            if(line.isEmpty()) {
                continue
            }
            if(line.startsWith("[") && line.endsWith("]")) {
                inTargetSection = line == "[$section]"
                continue
            }
            if(!inTargetSection) {
                continue
            }
            val separator = line.indexOf('=')
            if(separator < 0 || line.substring(0, separator).trim() != key) {
                continue
            }
            val rawValue = line.substring(separator + 1).trim()
            value = when {
                rawValue.length >= 2 && rawValue.startsWith("\"") && rawValue.endsWith("\"") ->
                    rawValue.substring(1, rawValue.length - 1)
                rawValue.length >= 2 && rawValue.startsWith("'") && rawValue.endsWith("'") ->
                    rawValue.substring(1, rawValue.length - 1)
                else -> rawValue
            }
            break
        }
    }
    return value
}

fun readLibfdxReleaseVersion(file: File?): String? {
    return readLibfdxTomlValue(file, "release", "fdxVersion")
}

fun readLibfdxReleaseGroup(file: File?): String? {
    return readLibfdxTomlValue(file, "release", "fdxGroup")
}

val libfdxBaseVersion = providers.gradleProperty("libfdx.version")
    .orElse(providers.provider { readLibfdxReleaseVersion(findLibfdxTomlFile()) })
    .get()
val libfdxGroup = providers.gradleProperty("libfdx.group")
    .orElse(providers.provider { readLibfdxReleaseGroup(findLibfdxTomlFile()) })
    .get()
val usePrebuiltRuntimeCoreNatives = providers.gradleProperty("libfdx.runtimeCore.usePrebuiltNatives")
    .map { it.toBoolean() }
    .orElse(false)

fun isTaskRequested(taskName: String): Boolean {
    return taskNames.any { it == taskName || it.endsWith(":$taskName") }
}

val isPrepareSnapshotDeploy = isTaskRequested("prepareSnapshotDeploy")
val isPrepareReleaseDeploy = isTaskRequested("prepareReleaseDeploy")
val isPublishSnapshot = isTaskRequested("publishSnapshot")
val isPublishRelease = isTaskRequested("publishRelease")
val isUploadToMavenCentral = isTaskRequested("uploadToMavenCentral")
val isZipStagingDeploy = isTaskRequested("zipStagingDeploy")
val isAnyPublishTask = requestedTaskBaseNames.any {
    it.equals("publish", ignoreCase = true)
            || (it.startsWith("publish", ignoreCase = true) && !it.equals("publishing", ignoreCase = true))
            || it.endsWith("Deploy", ignoreCase = true)
            || it.equals("zipStagingDeploy", ignoreCase = true)
            || it.equals("uploadToMavenCentral", ignoreCase = true)
}
val isReleaseLocalDeploy = isPrepareReleaseDeploy || isPublishRelease || isUploadToMavenCentral || isZipStagingDeploy
val isSnapshotPublishMode = isPrepareSnapshotDeploy || isPublishSnapshot
val libfdxVersion = if(isSnapshotPublishMode) "-SNAPSHOT" else libfdxBaseVersion

if(libfdxBaseVersion.endsWith("-SNAPSHOT")) {
    throw GradleException("The libFDX base version must not include -SNAPSHOT. Use the upcoming release version only.")
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

fun Project.configureLibfdxMavenRepository(deployDir: String? = null) {
    extensions.configure<PublishingExtension> {
        repositories {
            maven {
                name = "libfdxDeploy"
                url = when {
                    deployDir != null -> uri(deployDir)
                    isPrepareSnapshotDeploy -> uri(rootProject.layout.buildDirectory.dir("snapshot-deploy"))
                    isReleaseLocalDeploy -> uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
                    libfdxVersion.endsWith("-SNAPSHOT") -> uri(snapshotRepositoryUrl)
                    else -> uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
                }
                if(deployDir == null && !isPrepareSnapshotDeploy && !isReleaseLocalDeploy
                        && libfdxVersion.endsWith("-SNAPSHOT")) {
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
    val signingKey = System.getenv("SIGNING_KEY").orEmpty()
    val signingPassword = System.getenv("SIGNING_PASSWORD").orEmpty()
    if(signingKey.isNotEmpty() && signingPassword.isNotEmpty()) {
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
                "Gradle plugin for building libFDX web, desktop_native, PSP, and asset tasks."
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
    afterEvaluate {
        extensions.configure<PublishingExtension> {
            publications.register("release", MavenPublication::class.java) {
                from(components.getByName("release"))
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

    configureLibfdxJavaPublishArtifacts()
    configureLibfdxMavenRepository(providers.gradleProperty("libfdx.deployDir").orNull)
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

    tasks.register("publishSnapshot") {
        group = "publishing"
        description = "Publish the libFDX Gradle plugin snapshot marker and implementation artifacts."
        dependsOn("publish")
        onlyIf { libfdxVersion.endsWith("-SNAPSHOT") }
    }

    tasks.register("publishRelease") {
        group = "publishing"
        description = "Prepare the libFDX Gradle plugin release marker and implementation artifacts."
        dependsOn("prepareReleaseDeploy")
        onlyIf { !libfdxVersion.endsWith("-SNAPSHOT") }
    }
}

fun Project.configureLibraryPublishing() {
    if(isAnyPublishTask && !usePrebuiltRuntimeCoreNatives.get()) {
        findProject(":libfdx:runtime:core")?.let { runtimeCoreProject ->
            runtimeCoreProject.plugins.withId("java-library") {
                runtimeCoreProject.tasks.named("processResources").configure {
                    dependsOn(":libfdx:runtime:core:build_web_freetype_emscripten")
                }
            }
        }
    }

    val publishableProjects = subprojects.filter { it.path.startsWith(":libfdx:") }
    publishableProjects.forEach { publishProject ->
        publishProject.plugins.withId("java-library") {
            publishProject.configureJavaPublication()
        }
        publishProject.plugins.withId("com.android.library") {
            publishProject.configureAndroidPublication()
        }
    }

    val libraryPublishTasks = publishableProjects.map { project ->
        project.tasks.withType(PublishToMavenRepository::class.java)
    }
    val gradlePluginBuildDir = layout.projectDirectory.dir("libfdx/tools/gradle-plugin").asFile

    tasks.register<GradleBuild>("prepareGradlePluginSnapshotDeploy") {
        group = "publishing"
        description = "Prepare local snapshot deploy files for the libFDX Gradle plugin."
        dir = gradlePluginBuildDir
        tasks = listOf("prepareSnapshotDeploy")
        startParameter.projectProperties["libfdx.version"] = libfdxBaseVersion
        startParameter.projectProperties["libfdx.deployDir"] =
            rootProject.layout.buildDirectory.dir("snapshot-deploy").get().asFile.absolutePath
    }

    tasks.register<GradleBuild>("prepareGradlePluginReleaseDeploy") {
        group = "publishing"
        description = "Prepare local release deploy files for the libFDX Gradle plugin."
        dir = gradlePluginBuildDir
        tasks = listOf("prepareReleaseDeploy")
        startParameter.projectProperties["libfdx.version"] = libfdxBaseVersion
        startParameter.projectProperties["libfdx.deployDir"] =
            rootProject.layout.buildDirectory.dir("staging-deploy").get().asFile.absolutePath
    }

    tasks.register<GradleBuild>("publishGradlePluginSnapshot") {
        group = "publishing"
        description = "Publish the libFDX Gradle plugin snapshot marker and implementation artifacts."
        dir = gradlePluginBuildDir
        tasks = listOf("publishSnapshot")
        startParameter.projectProperties["libfdx.version"] = libfdxBaseVersion
    }

    tasks.register("prepareSnapshotDeploy") {
        group = "publishing"
        description = "Publish all libFDX snapshot artifacts to build/snapshot-deploy."
        dependsOn(libraryPublishTasks)
        dependsOn("prepareGradlePluginSnapshotDeploy")
        onlyIf { libfdxVersion.endsWith("-SNAPSHOT") }
    }

    tasks.register<Zip>("zipStagingDeploy") {
        group = "publishing"
        description = "Zip staged libFDX release artifacts for Central Portal upload."
        dependsOn(libraryPublishTasks)
        dependsOn("prepareGradlePluginReleaseDeploy")
        from(rootProject.layout.buildDirectory.dir("staging-deploy"))
        archiveFileName.set("staging-deploy.zip")
        destinationDirectory.set(rootProject.layout.buildDirectory)
        onlyIf { !libfdxVersion.endsWith("-SNAPSHOT") }
    }

    tasks.register("prepareReleaseDeploy") {
        group = "publishing"
        description = "Publish all libFDX release artifacts to build/staging-deploy and create staging-deploy.zip."
        dependsOn("zipStagingDeploy")
        onlyIf { !libfdxVersion.endsWith("-SNAPSHOT") }
    }

    tasks.register("publishSnapshot") {
        group = "publishing"
        description = "Publish all libFDX snapshot artifacts to the Central Portal snapshot repository."
        dependsOn(libraryPublishTasks)
        dependsOn("publishGradlePluginSnapshot")
        onlyIf { libfdxVersion.endsWith("-SNAPSHOT") }
    }

    tasks.register("uploadToMavenCentral") {
        group = "publishing"
        description = "Upload build/staging-deploy.zip to Maven Central Portal."
        dependsOn("zipStagingDeploy")
        onlyIf { !libfdxVersion.endsWith("-SNAPSHOT") }
        doLast {
            val stagingDir = rootProject.layout.buildDirectory.dir("staging-deploy").get().asFile
            val zipFile = rootProject.layout.buildDirectory.file("staging-deploy.zip").get().asFile
            if(!stagingDir.exists()) {
                throw GradleException("Staging directory $stagingDir does not exist. Run prepareReleaseDeploy first.")
            }
            if(!zipFile.exists()) {
                throw GradleException("Zip file ${zipFile.absolutePath} was not created.")
            }
            if(!Files.isReadable(Paths.get(zipFile.absolutePath))) {
                throw GradleException("Zip file ${zipFile.absolutePath} is not readable.")
            }

            val username = System.getenv("CENTRAL_PORTAL_USERNAME")
                ?: throw GradleException("CENTRAL_PORTAL_USERNAME environment variable not set")
            val password = System.getenv("CENTRAL_PORTAL_PASSWORD")
                ?: throw GradleException("CENTRAL_PORTAL_PASSWORD environment variable not set")
            val bundleName = URLEncoder.encode("$libfdxName-$libfdxVersion", "UTF-8")
            providers.exec {
                commandLine(
                    "curl",
                    "-u",
                    "$username:$password",
                    "--request",
                    "POST",
                    "--form",
                    "bundle=@${zipFile.absolutePath}",
                    "https://central.sonatype.com/api/v1/publisher/upload?name=$bundleName"
                )
            }.result.get()
        }
    }

    tasks.register("publishRelease") {
        group = "publishing"
        description = "Prepare and upload libFDX release artifacts to Maven Central Portal."
        dependsOn("prepareReleaseDeploy")
        finalizedBy("uploadToMavenCentral")
        onlyIf { !libfdxVersion.endsWith("-SNAPSHOT") }
    }
}

val publishTargetProperty = "libfdxPublishTarget"
val publishTarget = if(extensions.extraProperties.has(publishTargetProperty)) {
    extensions.extraProperties.get(publishTargetProperty).toString()
} else {
    throw GradleException("$publishTargetProperty must be configured before applying publish.gradle.kts")
}

when(publishTarget) {
    "LIBRARIES" -> configureLibraryPublishing()
    "GRADLE_PLUGIN" -> configureGradlePluginPublishing()
    else -> throw GradleException("$publishTargetProperty has unsupported value '$publishTarget'")
}
