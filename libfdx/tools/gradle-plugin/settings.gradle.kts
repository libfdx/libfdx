pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
        gradlePluginPortal()
        maven {
            url = uri("https://teavm.org/maven/repository/")
        }
    }
    versionCatalogs {
        create("libs") {
            from(files("../../../gradle/libs.versions.toml"))
        }
    }
}
