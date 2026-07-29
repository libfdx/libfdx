val libfdxRepositoryConsumersIncluded = gradle.parent == null
gradle.extensions.extraProperties.set(
    "libfdxRepositoryConsumersIncluded",
    libfdxRepositoryConsumersIncluded
)

pluginManagement {
    val repositoryConsumersIncluded = gradle.parent == null
    val tomlFile = java.io.File(settingsDir, "gradle/libs.versions.toml")
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
            ?: tomlValue("versions", key)
            ?: throw IllegalStateException(
                "Missing $systemKey, development.$key in local.properties, or [versions].$key in gradle/libs.versions.toml."
            )
    }

    val usePublishedLibfdx = when (val value = developmentValue("usePublishedLibfdx").lowercase()) {
        "true" -> true
        "false" -> false
        else -> throw IllegalArgumentException("development.usePublishedLibfdx must be true or false, got '$value'.")
    }
    gradle.extensions.extraProperties.set(
        "libfdxUsePublishedLibfdx",
        usePublishedLibfdx
    )
    val libfdxSnapshot = tomlValue("versions", "libfdxSnapshot")
        ?: throw IllegalStateException(
            "Missing [versions].libfdxSnapshot in gradle/libs.versions.toml."
        )
    if (repositoryConsumersIncluded && !usePublishedLibfdx) {
        includeBuild("libfdx/tools/gradle-plugin")
    }

    plugins {
        if (repositoryConsumersIncluded && usePublishedLibfdx) {
            id("io.github.libfdx") version libfdxSnapshot
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
include(":libfdx:extensions:ecs:core")
include(":libfdx:extensions:ecs:tooling")
include(":libfdx:extensions:scenario_validator:core")
include(":libfdx:extensions:scenario_validator:ui-kit")
include(":libfdx:extensions:physics:box2d:core")
include(":libfdx:extensions:physics:box3d:core")
include(":libfdx:extensions:physics:jolt:core")
include(":libfdx:extensions:ui:imgui:core")
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
include(":libfdx:extensions:graphics:shader-graph:core")
include(":libfdx:extensions:graphics:shader-graph:runtime")
include(":libfdx:extensions:graphics:shader-graph:g2d")
include(":libfdx:extensions:graphics:shader-graph:g3d")
include(":libfdx:extensions:graphics:shader-graph:ui-kit")
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
if (libfdxRepositoryConsumersIncluded) {
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
    include(":samples:base:starter-project:core")
    include(":samples:base:starter-project:platform:desktop")
    include(":samples:base:starter-project:platform:desktop_c")
    include(":samples:base:starter-project:platform:ios_c")
    include(":samples:base:starter-project:platform:android")
    include(":samples:base:starter-project:platform:web")
    include(":samples:2d:sprite-movement:core")
    include(":samples:2d:sprite-movement:platform:desktop")
    include(":samples:2d:sprite-movement:platform:desktop_c")
    include(":samples:2d:sprite-movement:platform:ios_c")
    include(":samples:2d:sprite-movement:platform:android")
    include(":samples:2d:sprite-movement:platform:web")
    include(":samples:2d:ecs-platformer:core")
    include(":samples:2d:ecs-platformer:platform:desktop")
    include(":samples:2d:ecs-platformer:platform:desktop_c")
    include(":samples:2d:ecs-platformer:platform:ios_c")
    include(":samples:2d:ecs-platformer:platform:android")
    include(":samples:2d:ecs-platformer:platform:web")
    include(":samples:2d:multiplayer-webrtc:core")
    include(":samples:2d:multiplayer-webrtc:platform:desktop")
    include(":samples:2d:multiplayer-webrtc:platform:android")
    include(":samples:2d:multiplayer-webrtc:platform:web")
    include(":samples:graphics:shader-graph:core")
    include(":samples:graphics:shader-graph:editor")
    include(":samples:graphics:shader-graph:platform:desktop")
}
