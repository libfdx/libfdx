val libfdxPublicationTaskNames = setOf(
    "prepareSnapshotDeploy",
    "prepareReleaseDeploy",
    "prepareGradlePluginSnapshotDeploy",
    "prepareGradlePluginReleaseDeploy",
    "publishSnapshot",
    "publishRelease",
    "uploadSnapshotDeploy",
    "uploadReleaseDeploy",
    "signReleaseDeploy",
    "verifyReleaseDeployArtifacts",
    "verifyPreparedReleaseDeployArtifacts",
    "zipReleaseDeploy",
    "publishToMavenLocal"
)

fun isLibfdxPublicationTask(taskPath: String): Boolean {
    val taskName = taskPath.substringAfterLast(":")
    return taskName in libfdxPublicationTaskNames ||
        taskName.startsWith("publishDeploy") && taskName.endsWith("PublicationToLibfdxDeployRepository")
}

val libfdxPublicationBuild = System.getenv("LIBFDX_PUBLICATION_BUILD")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.let { value ->
        when (value.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("LIBFDX_PUBLICATION_BUILD must be true or false, got '$value'.")
        }
    } == true || gradle.startParameter.taskNames.any(::isLibfdxPublicationTask)

pluginManagement {
    val publicationTaskNames = setOf(
        "prepareSnapshotDeploy",
        "prepareReleaseDeploy",
        "prepareGradlePluginSnapshotDeploy",
        "prepareGradlePluginReleaseDeploy",
        "publishSnapshot",
        "publishRelease",
        "uploadSnapshotDeploy",
        "uploadReleaseDeploy",
        "signReleaseDeploy",
        "verifyReleaseDeployArtifacts",
        "verifyPreparedReleaseDeployArtifacts",
        "zipReleaseDeploy",
        "publishToMavenLocal"
    )
    val publicationBuild = System.getenv("LIBFDX_PUBLICATION_BUILD")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { value ->
            when (value.lowercase()) {
                "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException("LIBFDX_PUBLICATION_BUILD must be true or false, got '$value'.")
            }
        } == true || gradle.startParameter.taskNames.any { taskPath ->
        val taskName = taskPath.substringAfterLast(":")
        taskName in publicationTaskNames ||
            taskName.startsWith("publishDeploy") && taskName.endsWith("PublicationToLibfdxDeployRepository")
    }
    val tomlFile = java.io.File(settingsDir, "libfdx.toml")
    val localProperties = java.util.Properties().also { properties ->
        val file = java.io.File(settingsDir, "local.properties")
        if (file.isFile) {
            file.inputStream().use { properties.load(it) }
        }
    }

    fun tomlValue(section: String, key: String): String? {
        var inTargetSection = false
        tomlFile.useLines { lines ->
            for (rawLine in lines) {
                val line = rawLine.substringBefore("#").trim()
                if (line.isEmpty()) {
                    continue
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    inTargetSection = line == "[$section]"
                    continue
                }
                val separator = line.indexOf('=')
                if (!inTargetSection || separator < 0 || line.substring(0, separator).trim() != key) {
                    continue
                }
                val value = line.substring(separator + 1).trim()
                return value.removeSurrounding("\"").removeSurrounding("'")
            }
        }
        return null
    }

    fun developmentValue(key: String): String {
        val systemKey = "libfdx.development.$key"
        return System.getProperty(systemKey)?.trim()?.takeIf { it.isNotEmpty() }
            ?: localProperties.getProperty("development.$key")?.trim()?.takeIf { it.isNotEmpty() }
            ?: tomlValue("development", key)
            ?: throw IllegalStateException("Missing $systemKey, development.$key in local.properties, or [development].$key in libfdx.toml.")
    }

    val usePublishedLibfdx = when (val value = developmentValue("usePublishedLibfdx").lowercase()) {
        "true" -> true
        "false" -> false
        else -> throw IllegalArgumentException("development.usePublishedLibfdx must be true or false, got '$value'.")
    }
    val fdxSnapshotVersion = tomlValue("release", "fdxSnapshotVersion")
        ?: throw IllegalStateException("Missing [release].fdxSnapshotVersion in libfdx.toml.")
    if (!publicationBuild && !usePublishedLibfdx) {
        includeBuild("libfdx/tools/gradle-plugin")
    }

    plugins {
        if (!publicationBuild && usePublishedLibfdx) {
            id("io.github.libfdx") version fdxSnapshotVersion
        }
    }

    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
        gradlePluginPortal()
    }
}

include(":libfdx:framework:math")
include(":libfdx:framework:json")
include(":libfdx:framework:collections")
include(":libfdx:framework:application")
include(":libfdx:framework:fdx:core")
include(":libfdx:framework:fdx:fdx-build")
include(":libfdx:framework:fdx:platform:shared")
include(":libfdx:framework:fdx:platform:desktop")
include(":libfdx:framework:fdx:platform:android")
include(":libfdx:framework:fdx:platform:web")
include(":libfdx:framework:display")
include(":libfdx:framework:files")
include(":libfdx:framework:input")
include(":libfdx:framework:net")
include(":libfdx:framework:storage")
include(":libfdx:framework:assets:manager")
include(":libfdx:framework:assets:loaders")
include(":libfdx:framework:graphics")
include(":libfdx:framework:camera")
include(":libfdx:framework:g2d")
include(":libfdx:framework:g3d")
include(":libfdx:framework:ui-kit")
include(":libfdx:extensions:ecs")
include(":libfdx:extensions:scenario_validator:core")
include(":libfdx:extensions:scenario_validator:ui-kit")
include(":libfdx:tools:font")
include(":libfdx:tools:project-generator:core")
include(":libfdx:tools:project-generator:ui")
include(":libfdx:tools:project-generator:platform:desktop")
include(":libfdx:tools:project-generator:platform:web")
include(":libfdx:extensions:graphics:gl:core")
include(":libfdx:extensions:graphics:gl:platform:desktop")
include(":libfdx:extensions:graphics:gl:platform:desktop_c")
include(":libfdx:extensions:graphics:gl:platform:web")
include(":libfdx:extensions:graphics:vulkan:core")
include(":libfdx:extensions:graphics:vulkan:platform:desktop")
include(":libfdx:extensions:graphics:vulkan:platform:desktop_c")
include(":libfdx:extensions:graphics:vulkan:platform:android_jni")
include(":libfdx:extensions:graphics:d3d12:core")
include(":libfdx:extensions:graphics:wgpu:core")
include(":libfdx:extensions:graphics:wgpu:platform:desktop_jni")
include(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm")
include(":libfdx:extensions:graphics:wgpu:platform:android_jni")
include(":libfdx:extensions:graphics:wgpu:platform:web")
include(":libfdx:extensions:net:webrtc:core")
include(":libfdx:extensions:net:webrtc:signaling_server")
include(":libfdx:extensions:net:webrtc:platform:desktop_jni")
include(":libfdx:extensions:net:webrtc:platform:web")
include(":libfdx:extensions:net:webrtc:platform:android_jni")
include(":libfdx:backends:desktop")
include(":libfdx:backends:c_shared")
include(":libfdx:backends:desktop_c")
include(":libfdx:backends:ios_c")
include(":libfdx:backends:psp")
include(":libfdx:backends:android")
include(":libfdx:backends:web")
if (!libfdxPublicationBuild) {
    include(":tests:core")
    include(":tests:platform:desktop")
    include(":tests:platform:desktop_c")
    include(":tests:platform:android")
    include(":tests:platform:web")
    include(":tests:platform:psp")
    include(":tests:platform:plugin")
    include(":benchmark:core")
    include(":benchmark:platform:desktop")
    include(":benchmark:platform:desktop_c")
    include(":benchmark:platform:plugin")
    include(":samples:basic:core")
    include(":samples:basic:platform:desktop")
    include(":samples:basic:platform:plugin")
    include(":samples:basic:platform:desktop_c")
    include(":samples:basic:platform:ios_c")
    include(":samples:basic:platform:android")
    include(":samples:basic:platform:web")
    include(":samples:ecs-platformer:core")
    include(":samples:ecs-platformer:platform:desktop")
    include(":samples:ecs-platformer:platform:desktop_c")
    include(":samples:ecs-platformer:platform:ios_c")
    include(":samples:ecs-platformer:platform:android")
    include(":samples:ecs-platformer:platform:web")
    include(":samples:multiplayer:2d-webrtc:core")
    include(":samples:multiplayer:2d-webrtc:platform:desktop")
    include(":samples:multiplayer:2d-webrtc:platform:plugin")
    include(":samples:multiplayer:2d-webrtc:platform:android")
    include(":samples:multiplayer:2d-webrtc:platform:web")
}
