import io.github.libfdx.build.LibExt
import org.gradle.api.publish.tasks.GenerateModuleMetadata

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
        implementation("${LibExt.fdxGroup}:$artifact:${LibExt.publishedLibfdxVersion}")
    }
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
