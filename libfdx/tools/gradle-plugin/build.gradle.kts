import io.github.libfdx.build.LibExt
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.testing.Test

plugins {
    id("maven-publish")
    `kotlin-dsl`
    `java-gradle-plugin`
}

LibExt.configure(rootProject.projectDir)

allprojects {
    group = LibExt.fdxGroup
    version = LibExt.fdxVersion
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
apply(from = "../../../buildSrc/src/main/kotlin/publish.gradle.kts")

dependencies {
    implementation(libs.teavm.gradle.plugin)
    libfdxGradlePluginDependencyArtifacts.forEach { artifact ->
        implementation("${LibExt.fdxGroup}:$artifact:${LibExt.pluginBootstrapLibfdxVersion}")
    }
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Project generation must use the writer from this checkout. The included plugin build
// deliberately compiles against previously published bootstrap artifacts, which would
// otherwise make local desktop-C generator fixes invisible until after publication.
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
