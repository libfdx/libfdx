import io.github.libfdx.build.LibExt
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.testing.Test

plugins {
    id("maven-publish")
    alias(libs.plugins.easy.publishing)
    `kotlin-dsl`
    `java-gradle-plugin`
}

LibExt.configure(rootProject.projectDir)

val libfdxGradlePluginDependencyArtifacts = listOf(
    "tools_font",
    "graphics",
    "net",
    "backend_web",
    "backend_desktop_c",
    "backend_ios_c",
    "backend_psp"
)

extra["libfdxGradlePluginDependencyArtifacts"] = libfdxGradlePluginDependencyArtifacts
val libfdxReleaseRequested = extensions.extraProperties.get("easyPublishing.releaseRequested") as Boolean
val libfdxSelectedVersion = if (libfdxReleaseRequested) LibExt.fdxVersion else LibExt.fdxSnapshotVersion

easyPublishing {
    groupId.set(LibExt.fdxGroup)
    releaseVersion.set(LibExt.fdxVersion)
    snapshotVersion.set(LibExt.fdxSnapshotVersion)

    snapshotRepositoryUrl.set("https://central.sonatype.com/repository/maven-snapshots/")
    releaseRepositoryUrl.set("https://central.sonatype.com")
    username.set(providers.environmentVariable("CENTRAL_PORTAL_USERNAME"))
    password.set(providers.environmentVariable("CENTRAL_PORTAL_PASSWORD"))
    signingKey.set(providers.environmentVariable("SIGNING_KEY"))
    signingPassword.set(providers.environmentVariable("SIGNING_PASSWORD"))
    automaticRelease.set(
        providers.environmentVariable("CENTRAL_PUBLISHING_TYPE")
            .map { it.equals("AUTOMATIC", ignoreCase = true) }
            .orElse(false)
    )

    pomName.set("libFDX Gradle plugin")
    pomDescription.set("Gradle plugin for building libFDX web, desktop_c, PSP, and asset tasks.")
    projectUrl.set("https://github.com/libfdx/libfdx")
    developerId.set("Xpe")
    developerName.set("Natan")
    scmUrl.set("https://github.com/libfdx/libfdx")
    scmConnection.set("scm:git:https://github.com/libfdx/libfdx.git")
    scmDeveloperConnection.set("scm:git:ssh://git@github.com/libfdx/libfdx.git")
}

dependencies {
    implementation(libs.teavm.gradle.plugin)
    libfdxGradlePluginDependencyArtifacts.forEach { artifact ->
        implementation("${LibExt.fdxGroup}:$artifact:$libfdxSelectedVersion")
    }
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Project generation must use the writer from this checkout. The included plugin build
// compiles against libFDX artifacts at the same selected publication version, but that
// dependency must not hide local desktop-C generator fixes until after publication.
sourceSets {
    main {
        java.srcDir("../../backends/desktop_c/src/main/java")
        java.include("io/github/libfdx/backend/desktopc/NativeProjectWriter.java")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = false
}

val moduleName = "gradle-plugin"

java {
    withSourcesJar()
    withJavadocJar()
}

gradlePlugin {
    plugins {
        create("libfdx") {
            id = "io.github.libfdx"
            implementationClass = "io.github.libfdx.gradle.LibfdxGradlePlugin"
        }
    }
}

publishing {
    publications {
        withType(org.gradle.api.publish.maven.MavenPublication::class).configureEach {
            if (name == "pluginMaven") {
                artifactId = moduleName
            }
        }
    }
}
