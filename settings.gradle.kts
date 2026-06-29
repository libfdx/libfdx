pluginManagement {
    val tomlFile = java.io.File(settingsDir, "libfdx.toml")
    val localProperties = java.util.Properties().also { properties ->
        val file = java.io.File(settingsDir, "local.properties")
        if (file.isFile) {
            file.inputStream().use { properties.load(it) }
        }
    }

    fun tomlDevelopmentValue(key: String): String? {
        var inDevelopmentSection = false
        tomlFile.useLines { lines ->
            for (rawLine in lines) {
                val line = rawLine.substringBefore("#").trim()
                if (line.isEmpty()) {
                    continue
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    inDevelopmentSection = line == "[development]"
                    continue
                }
                val separator = line.indexOf('=')
                if (!inDevelopmentSection || separator < 0 || line.substring(0, separator).trim() != key) {
                    continue
                }
                val value = line.substring(separator + 1).trim()
                return value.removeSurrounding("\"").removeSurrounding("'")
            }
        }
        return null
    }

    fun developmentValue(key: String): String {
        return localProperties.getProperty("development.$key")?.trim()?.takeIf { it.isNotEmpty() }
            ?: tomlDevelopmentValue(key)
            ?: throw IllegalStateException("Missing development.$key in local.properties or libfdx.toml.")
    }

    val usePublishedLibfdx = when (val value = developmentValue("usePublishedLibfdx").lowercase()) {
        "true" -> true
        "false" -> false
        else -> throw IllegalArgumentException("development.usePublishedLibfdx must be true or false, got '$value'.")
    }
    val publishedLibfdxVersion = developmentValue("publishedLibfdxVersion")
    if (!usePublishedLibfdx) {
        includeBuild("libfdx/tools/gradle-plugin")
    }

    plugins {
        if (usePublishedLibfdx) {
            id("io.github.libfdx") version publishedLibfdxVersion
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
include(":libfdx:validation:scenario-validator")
include(":libfdx:validation:scenario-validator-ui-kit")
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
include(":samples:multiplayer:2d-webrtc:core")
include(":samples:multiplayer:2d-webrtc:platform:desktop")
include(":samples:multiplayer:2d-webrtc:platform:plugin")
include(":samples:multiplayer:2d-webrtc:platform:android")
include(":samples:multiplayer:2d-webrtc:platform:web")
