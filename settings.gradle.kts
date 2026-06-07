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
        maven {
            url = uri("http://teavm.org/maven/repository/")
            isAllowInsecureProtocol = true
        }
    }
}

include(":libfdx:foundation:math")
include(":libfdx:foundation:json")
include(":libfdx:runtime:application")
include(":libfdx:runtime:fdx:core")
include(":libfdx:runtime:fdx:platform:shared")
include(":libfdx:runtime:fdx:platform:desktop")
include(":libfdx:runtime:fdx:platform:android")
include(":libfdx:runtime:fdx:platform:web")
include(":libfdx:runtime:display")
include(":libfdx:runtime:files")
include(":libfdx:runtime:input")
include(":libfdx:assets:manager")
include(":libfdx:assets:loaders")
include(":libfdx:graphics:api")
include(":libfdx:graphics:g2d")
include(":libfdx:graphics:g3d")
include(":libfdx:ui:ui-kit")
include(":libfdx:validation:scenario-validator")
include(":libfdx:validation:scenario-validator-ui-kit")
include(":libfdx:tools:font")
include(":libfdx:tools:project-generator:core")
include(":libfdx:tools:project-generator:ui")
include(":libfdx:tools:project-generator:platform:desktop")
include(":libfdx:tools:project-generator:platform:web")
include(":libfdx:extensions:graphics:gl:core")
include(":libfdx:extensions:graphics:gl:platform:desktop")
include(":libfdx:extensions:graphics:gl:platform:desktop_native")
include(":libfdx:extensions:graphics:gl:platform:web")
include(":libfdx:extensions:graphics:vulkan:core")
include(":libfdx:extensions:graphics:vulkan:platform:desktop")
include(":libfdx:extensions:graphics:vulkan:platform:desktop_native")
include(":libfdx:extensions:graphics:vulkan:platform:android_jni")
include(":libfdx:extensions:graphics:wgpu:core")
include(":libfdx:extensions:graphics:wgpu:platform:desktop_jni")
include(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm")
include(":libfdx:extensions:graphics:wgpu:platform:android_jni")
include(":libfdx:extensions:graphics:wgpu:platform:web")
include(":libfdx:backends:desktop")
include(":libfdx:backends:teavm_shared")
include(":libfdx:backends:desktop_native")
include(":libfdx:backends:psp")
include(":libfdx:backends:android")
include(":libfdx:backends:web")
include(":tests:core")
include(":tests:platform:desktop")
include(":tests:platform:desktop_native")
include(":tests:platform:android")
include(":tests:platform:web")
include(":tests:platform:psp")
include(":benchmark:core")
include(":benchmark:platform:desktop")
include(":benchmark:platform:desktop_native")
include(":samples:basic:core")
include(":samples:basic:platform:desktop")
include(":samples:basic:platform:desktop_native")
include(":samples:basic:platform:android")
include(":samples:basic:platform:web")
