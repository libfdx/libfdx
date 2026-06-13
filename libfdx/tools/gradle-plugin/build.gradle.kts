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
    implementation("${LibExt.fdxGroup}:tools_font:${LibExt.publishedLibfdxVersion}")
    implementation("${LibExt.fdxGroup}:tools_shader:${LibExt.publishedLibfdxVersion}")
    implementation("${LibExt.fdxGroup}:backend_web:${LibExt.publishedLibfdxVersion}")
    implementation("${LibExt.fdxGroup}:backend_desktop_native:${LibExt.publishedLibfdxVersion}")
    implementation("${LibExt.fdxGroup}:backend_psp:${LibExt.publishedLibfdxVersion}")
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
