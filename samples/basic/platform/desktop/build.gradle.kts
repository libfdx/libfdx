import io.github.libfdx.build.LibExt

import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("java")
}

group = "${LibExt.fdxGroup}.samples.basic"


java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

val glRuntimeClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val vulkanRuntimeClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val wgpuRuntimeClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

base {
    archivesName.set("sample_basic_desktop")
}

dependencies {
    implementation(project(":samples:basic:core"))
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:application:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:display:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:wgpu_core:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:backend_desktop:${LibExt.publishedLibfdxVersion}")

        glRuntimeClasspath("${LibExt.fdxGroup}:gl_desktop:${LibExt.publishedLibfdxVersion}")
        vulkanRuntimeClasspath("${LibExt.fdxGroup}:vulkan_desktop:${LibExt.publishedLibfdxVersion}")
        wgpuRuntimeClasspath("${LibExt.fdxGroup}:wgpu_desktop_ffm:${LibExt.publishedLibfdxVersion}")
    } else {
        implementation(project(":libfdx:runtime:application"))
        implementation(project(":libfdx:runtime:display"))
        implementation(project(":libfdx:extensions:graphics:wgpu:core"))
        implementation(project(":libfdx:backends:desktop"))

        glRuntimeClasspath(project(":libfdx:extensions:graphics:gl:platform:desktop"))
        vulkanRuntimeClasspath(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
        wgpuRuntimeClasspath(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
    }
}

val sampleMainClass = "io.github.libfdx.samples.basic.desktop.BasicDesktopLauncher"
val desktopJvmDistDir = layout.buildDirectory.dir("dist/desktop-jvm")

fun registerDesktopSample(providerName: String, displayName: String, providerClasspath: FileCollection) {
    val taskBaseName = "basic_desktop_$providerName"
    val releaseClasspath = sourceSets["main"].runtimeClasspath + providerClasspath
    val launchDefaults = layout.buildDirectory.file(
            "generated/desktop-jvm/$taskBaseName/libfdx-desktop-launch.properties")
    val writeLaunchDefaults = tasks.register("${taskBaseName}_write_launch_defaults") {
        outputs.file(launchDefaults)
        doLast {
            val output = launchDefaults.get().asFile
            output.parentFile.mkdirs()
            output.writeText(
                    "graphics=$providerName${System.lineSeparator()}graphicsLabel=$displayName${System.lineSeparator()}",
                    Charsets.UTF_8)
        }
    }
    val buildTask = tasks.register<Jar>("${taskBaseName}_build") {
        group = "application"
        description = "Builds the basic desktop sample $displayName release jar."
        dependsOn("classes", releaseClasspath, writeLaunchDefaults)
        archiveFileName.set("$taskBaseName.jar")
        destinationDirectory.set(desktopJvmDistDir)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        isZip64 = true
        manifest {
            attributes(
                    "Main-Class" to sampleMainClass,
                    "Multi-Release" to "true",
                    "Enable-Native-Access" to "ALL-UNNAMED")
        }
        exclude("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF")
        from({
            releaseClasspath.files
                    .filter { it.exists() }
                    .map { if (it.isDirectory) it else zipTree(it) }
        })
        from(launchDefaults.map { it.asFile }) {
            rename { "libfdx-desktop-launch.properties" }
        }
    }
    tasks.register<JavaExec>("${taskBaseName}_run") {
        group = "application"
        description = "Runs the basic desktop sample with $displayName."
        dependsOn(buildTask)
        classpath = releaseClasspath
        mainClass.set(sampleMainClass)
        workingDir = rootProject.projectDir
        javaLauncher.set(javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
        })
        jvmArgs("-Dorg.lwjgl.system.stackSize=1048576", "--enable-native-access=ALL-UNNAMED")
        systemProperty("libfdx.sample.graphics", providerName)
        systemProperty("libfdx.sample.graphicsLabel", displayName)
        System.getProperty("libfdx.sample.exitAfterFrames")?.takeIf { it.isNotBlank() }?.let { frames ->
            systemProperty("libfdx.sample.exitAfterFrames", frames)
        }
    }
}

registerDesktopSample("gl", "GL", glRuntimeClasspath)
registerDesktopSample("wgpu", "WGPU", wgpuRuntimeClasspath)
registerDesktopSample("vulkan", "Vulkan", vulkanRuntimeClasspath)
