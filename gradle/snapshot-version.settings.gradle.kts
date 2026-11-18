// Both the framework and its included Gradle plugin publish from this Git checkout.
val commit = providers.exec {
    workingDir(settingsDir)
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.get().trim()

dependencyResolutionManagement.versionCatalogs.maybeCreate("libs")
    .version("libfdxSnapshot", "${commit.take(8)}-SNAPSHOT")
