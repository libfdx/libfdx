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
        maven {
            url = uri("../../../build/release-deploy")
        }
        maven {
            url = uri("../../../build/snapshot-deploy")
        }
        google()
        mavenCentral()
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../../../gradle/libs.versions.toml"))
        }
    }
}
