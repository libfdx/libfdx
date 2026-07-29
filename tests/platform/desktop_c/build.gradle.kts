plugins {
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("tests_desktop_c")
}

dependencies {
    implementation(project(":tests:core"))
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_desktop_c:${libs.versions.libfdxSnapshot.get()}")
        runtimeOnly("${libs.versions.libfdxGroup.get()}:gl_desktop_c:${libs.versions.libfdxSnapshot.get()}")
        runtimeOnly("${libs.versions.libfdxGroup.get()}:vulkan_desktop_c:${libs.versions.libfdxSnapshot.get()}")
    } else {
        implementation(project(":libfdx:backends:desktop_c"))
        runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop_c"))
        runtimeOnly(project(":libfdx:extensions:graphics:vulkan:platform:desktop_c"))
    }
}

libfdx {
    assets(rootProject.layout.projectDirectory.dir("tests/assets"))

    desktopC {
        minHeapSize.set(64)
        maxHeapSize.set(1024)
        obfuscated.set(false)

        target("opengl") {
            displayName.set("desktop C OpenGL graphics tests")
            mainClass.set("io.github.libfdx.tests.desktopc.DesktopCOpenGLTestLauncher")
            targetFileName.set("libfdx-tests-opengl-desktop-c")
        }
        target("vulkan") {
            displayName.set("desktop C Vulkan graphics tests")
            mainClass.set("io.github.libfdx.tests.desktopc.DesktopCVulkanTestLauncher")
            targetFileName.set("libfdx-tests-vulkan-desktop-c")
        }
    }
}
