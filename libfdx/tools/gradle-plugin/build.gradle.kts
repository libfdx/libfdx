import io.github.libfdx.build.LibExt
import org.gradle.api.publish.tasks.GenerateModuleMetadata

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

LibExt.configure(rootProject.projectDir)

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven {
        url = uri("http://teavm.org/maven/repository/")
        isAllowInsecureProtocol = true
    }
}

allprojects {
    group = LibExt.fdxGroup
    version = LibExt.fdxVersion

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
        maven {
            url = uri("http://teavm.org/maven/repository/")
            isAllowInsecureProtocol = true
        }
    }
}

extra["libfdxPublishTarget"] = "GRADLE_PLUGIN"
apply(from = "../../../buildSrc/src/main/kotlin/publish.gradle.kts")

dependencies {
    implementation(libs.teavm.gradle.plugin)
    implementation(project(":libfdx:tools:font"))
    implementation(project(":libfdx:tools:shader"))
    implementation(project(":libfdx:backends:web"))
    implementation(project(":libfdx:backends:desktop_native"))
    implementation(project(":libfdx:backends:psp"))
}

tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = false
}

gradlePlugin {
    plugins {
        create("libfdx") {
            id = "io.github.libfdx"
            implementationClass = "io.github.libfdx.gradle.LibfdxGradlePlugin"
        }
    }
}
