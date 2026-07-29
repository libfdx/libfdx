import org.teavm.gradle.api.OptimizationLevel

plugins {
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("tests_psp")
}

dependencies {
    implementation(project(":tests:core"))
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_psp:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:g2d:${libs.versions.libfdxSnapshot.get()}")
        implementation("${libs.versions.libfdxGroup.get()}:ui_kit:${libs.versions.libfdxSnapshot.get()}")
    } else {
        implementation(project(":libfdx:backends:psp"))
        implementation(project(":libfdx:framework:g2d"))
        implementation(project(":libfdx:framework:ui-kit"))
    }
}

libfdx {
    assets(rootProject.layout.projectDirectory.dir("tests/assets"))

    bitmapFont("psp_test_bitmap") {
        sourceFile.set(rootProject.layout.projectDirectory.file("tests/assets/font/freetype/lsans.ttf"))
        outputDir.set(rootProject.layout.projectDirectory.dir("tests/assets"))
        assetPath.set("font/bitmap")
        size.set(24)
        padding.set(2)
        maxTextureSize.set(512)
    }

    psp {
        optimization.set(OptimizationLevel.BALANCED)
        debugInformation.set(true)
        debugMemory.set(false)
        maxHeapSize.set(32)

        target("test") {
            displayName.set("libfdx PSP shared test selector")
            mainClass.set("io.github.libfdx.tests.psp.PspTestSelectorLauncher")
            targetFileName.set("libfdx-tests-psp")
        }
    }
}
