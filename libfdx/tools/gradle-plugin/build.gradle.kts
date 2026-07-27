import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar

plugins {
    id("maven-publish")
    alias(libs.plugins.easyPublishing)
    `kotlin-dsl`
    `java-gradle-plugin`
}


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
        implementation("${libs.versions.libfdxGroup.get()}:$artifact:$libfdxSelectedVersion")
    }
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Project generation must use the writer from this checkout. The included plugin build
// compiles against libFDX artifacts at the same selected publication version, but that
// dependency must not hide local desktop-C generator or shader-validation API changes
// until after publication.
sourceSets {
    main {
        java.srcDir("../../backends/desktop_c/src/main/java")
        java.srcDir("../../framework/graphics/src/main/java")
        java.include("io/github/libfdx/backend/desktopc/NativeProjectWriter.java")
        java.include("io/github/libfdx/graphics/shader/ShaderProfile.java")
        java.include("io/github/libfdx/graphics/shader/ShaderProfileValidator.java")
        java.include("io/github/libfdx/graphics/shader/ShaderValidationDiagnostic.java")
        java.include("io/github/libfdx/graphics/shader/ShaderValidationResult.java")
        java.include("io/github/libfdx/graphics/shader/ShaderValidationSeverity.java")
    }
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
