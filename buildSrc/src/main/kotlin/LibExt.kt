package io.github.libfdx.build

import java.io.File

object LibExt {
    private var loadedConfig: Config? = null
    private var projectProperties: Map<String, String> = emptyMap()

    val fdxGroup: String
        get() = property("libfdx.group") ?: config().fdxGroup

    val fdxVersion: String
        get() = property("libfdx.version") ?: config().fdxVersion

    val usePublishedLibfdx: Boolean
        get() = property("libfdx.usePublishedLibfdx")?.let { parseBooleanValue("libfdx.usePublishedLibfdx", it) }
            ?: config().usePublishedLibfdx

    val publishedLibfdxVersion: String
        get() = property("libfdx.publishedVersion") ?: config().publishedLibfdxVersion

    fun configure(startDirectory: File, properties: Map<String, String>) {
        projectProperties = properties
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

    private fun property(name: String): String? {
        return projectProperties[name]?.takeIf { it.isNotBlank() }
    }

    private fun readConfig(startDirectory: File): Config {
        val file = findLibfdxTomlFile(startDirectory)
            ?: throw IllegalStateException("Could not find libfdx.toml from ${startDirectory.absolutePath}.")
        return Config(
            fdxGroup = readRequiredTomlValue(file, "release", "fdxGroup"),
            fdxVersion = readRequiredTomlValue(file, "release", "fdxVersion"),
            usePublishedLibfdx = parseBooleanValue(
                "libfdx.toml [development].usePublishedLibfdx",
                readRequiredTomlValue(file, "development", "usePublishedLibfdx")
            ),
            publishedLibfdxVersion = readRequiredTomlValue(file, "development", "publishedLibfdxVersion")
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
        val fdxGroup: String,
        val fdxVersion: String,
        val usePublishedLibfdx: Boolean,
        val publishedLibfdxVersion: String
    )
}
