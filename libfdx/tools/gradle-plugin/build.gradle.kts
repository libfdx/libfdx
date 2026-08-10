import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar

plugins {
    id("maven-publish")
    alias(libs.plugins.easyPublishing)
    `kotlin-dsl`
    `java-gradle-plugin`
}

System.getProperty("libfdx.compositeBuildDir")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.let { isolatedRootPath ->
        layout.buildDirectory.set(file(isolatedRootPath).resolve("_gradle-plugin"))
    }

val libfdxReleaseRequested = extensions.extraProperties.get("easyPublishing.releaseRequested") as Boolean
val libfdxSelectedVersion = if (libfdxReleaseRequested) libs.versions.libfdxRelease.get() else libs.versions.libfdxSnapshot.get()

easyPublishing {
    groupId.set(libs.versions.libfdxGroup.get())
    releaseVersion.set(libs.versions.libfdxRelease.get())
    snapshotVersion.set(libs.versions.libfdxSnapshot.get())

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
    pomDescription.set("Gradle plugin for launching and building libFDX platform targets and generated assets.")
    projectUrl.set("https://github.com/libfdx/libfdx")
    developerId.set("Xpe")
    developerName.set("Natan")
    scmUrl.set("https://github.com/libfdx/libfdx")
    scmConnection.set("scm:git:https://github.com/libfdx/libfdx.git")
    scmDeveloperConnection.set("scm:git:ssh://git@github.com/libfdx/libfdx.git")
}

dependencies {
    implementation(libs.teavm.gradle.plugin)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = false
}

tasks.withType<Jar>().configureEach {
    manifest.attributes["Implementation-Version"] = libfdxSelectedVersion
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
