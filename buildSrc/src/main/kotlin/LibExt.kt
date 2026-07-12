package io.github.libfdx.build

import java.io.File
import java.util.Properties

object LibExt {
    private var loadedConfig: Config? = null

    val fdxGroup: String
        get() = config().fdxGroup

    val fdxVersion: String
        get() = config().fdxVersion

    val usePublishedLibfdx: Boolean
        get() = config().usePublishedLibfdx

    val publishedLibfdxVersion: String
        get() = config().publishedLibfdxVersion

    val pluginBootstrapLibfdxVersion: String
        get() = config().pluginBootstrapLibfdxVersion

    val rootDirectory: File
        get() = config().rootDirectory

    fun configure(startDirectory: File) {
        loadedConfig = readConfig(startDirectory)
    }

    private fun config(): Config {
        val loaded = loadedConfig
        if (loaded != null) {
            return loaded
        }
        val config = readConfig(File(System.getProperty("user.dir")))
        loadedConfig = config
        return config
    }

    private fun readConfig(startDirectory: File): Config {
        val file = findLibfdxTomlFile(startDirectory)
            ?: throw IllegalStateException("Could not find libfdx.toml from ${startDirectory.absolutePath}.")
        val rootDirectory = file.parentFile
        val localProperties = readLocalProperties(rootDirectory)
        val usePublishedLibfdxValue = readDevelopmentValue(
            file,
            localProperties,
            "usePublishedLibfdx"
        )
        val publishedLibfdxVersionValue = readDevelopmentValue(
            file,
            localProperties,
            "publishedLibfdxVersion"
        )
        val pluginBootstrapLibfdxVersionValue = readDevelopmentValue(
            file,
            localProperties,
            "pluginBootstrapLibfdxVersion"
        )
        val fdxVersion = readRequiredTomlValue(file, "release", "fdxVersion")
        return Config(
            rootDirectory = rootDirectory,
            fdxGroup = readRequiredTomlValue(file, "release", "fdxGroup"),
            fdxVersion = fdxVersion,
            usePublishedLibfdx = parseBooleanValue(
                usePublishedLibfdxValue.source,
                usePublishedLibfdxValue.value
            ),
            publishedLibfdxVersion = publishedLibfdxVersionValue.value,
            pluginBootstrapLibfdxVersion = pluginBootstrapLibfdxVersionValue.value
        )
    }

    private fun findLibfdxTomlFile(startDirectory: File): File? {
        var directory: File? = if (startDirectory.isFile) startDirectory.parentFile else startDirectory
        while (directory != null) {
            val candidate = File(directory, "libfdx.toml")
            if (candidate.isFile) {
                return candidate
            }
            directory = directory.parentFile
        }
        return null
    }

    private fun readRequiredTomlValue(file: File, section: String, key: String): String {
        return readTomlValue(file, section, key)
            ?: throw IllegalStateException("Missing required libfdx.toml value [$section].$key.")
    }

    private fun readDevelopmentValue(file: File, localProperties: Properties, key: String): ConfigValue {
        val localKey = "development.$key"
        val localValue = localProperties.getProperty(localKey)?.trim()?.takeIf { it.isNotEmpty() }
        if (localValue != null) {
            return ConfigValue("local.properties $localKey", localValue)
        }
        return ConfigValue(
            "libfdx.toml [development].$key",
            readRequiredTomlValue(file, "development", key)
        )
    }

    private fun readLocalProperties(rootDirectory: File): Properties {
        val properties = Properties()
        val file = File(rootDirectory, "local.properties")
        if (file.isFile) {
            file.inputStream().use { properties.load(it) }
        }
        return properties
    }

    private fun readTomlValue(file: File, section: String, key: String): String? {
        var inTargetSection = false
        file.useLines { lines ->
            for (rawLine in lines) {
                val line = rawLine.substringBefore("#").trim()
                if (line.isEmpty()) {
                    continue
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    inTargetSection = line == "[$section]"
                    continue
                }
                if (!inTargetSection) {
                    continue
                }
                val separator = line.indexOf('=')
                if (separator < 0 || line.substring(0, separator).trim() != key) {
                    continue
                }
                val rawValue = line.substring(separator + 1).trim()
                return when {
                    rawValue.length >= 2 && rawValue.startsWith("\"") && rawValue.endsWith("\"") ->
                        rawValue.substring(1, rawValue.length - 1)
                    rawValue.length >= 2 && rawValue.startsWith("'") && rawValue.endsWith("'") ->
                        rawValue.substring(1, rawValue.length - 1)
                    else -> rawValue
                }
            }
        }
        return null
    }

    private fun parseBooleanValue(name: String, value: String): Boolean {
        return when (value.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("$name must be true or false, got '$value'.")
        }
    }

    private data class Config(
        val rootDirectory: File,
        val fdxGroup: String,
        val fdxVersion: String,
        val usePublishedLibfdx: Boolean,
        val publishedLibfdxVersion: String,
        val pluginBootstrapLibfdxVersion: String
    )

    private data class ConfigValue(
        val source: String,
        val value: String
    )
}
