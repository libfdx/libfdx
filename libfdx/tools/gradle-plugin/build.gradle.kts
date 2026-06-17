import io.github.libfdx.build.LibExt
import org.gradle.api.publish.tasks.GenerateModuleMetadata

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

LibExt.configure(rootProject.projectDir)

allprojects {
    group = LibExt.fdxGroup
    version = LibExt.fdxVersion
}

extra["libfdxPublishTarget"] = "GRADLE_PLUGIN"
apply(from = "../../../buildSrc/src/main/kotlin/publish.gradle.kts")

dependencies {
    implementation(libs.teavm.gradle.plugin)
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:tools_font:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:shader_compiler:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:backend_web:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:backend_desktop_c:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:backend_ios_c:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:backend_psp:${LibExt.publishedLibfdxVersion}")
    } else {
        implementation(libs.teavm.tooling)
        implementation(libs.teavm.classlib)
        implementation(libs.teavm.interop)
        implementation(libs.teavm.jso)
        implementation(libs.teavm.jso.apis)
        implementation(libs.teavm.jso.impl)
        implementation("org.teavm:teavm-platform:${libs.versions.teavm.get()}")
    }
}

if (!LibExt.usePublishedLibfdx) {
    sourceSets {
        main {
            java.srcDirs(
                "../../../libfdx/foundation/math/src/main/java",
                "../../../libfdx/runtime/fdx/core/src/main/java",
                "../../../libfdx/runtime/display/src/main/java",
                "../../../libfdx/runtime/files/src/main/java",
                "../../../libfdx/runtime/input/src/main/java",
                "../../../libfdx/runtime/application/src/main/java",
                "../../../libfdx/graphics/api/src/main/java",
                "../../../libfdx/extensions/graphics/gl/core/src/main/java",
                "../../../libfdx/extensions/graphics/vulkan/core/src/main/java",
                "../../../libfdx/tools/font/src/main/java",
                "../../../libfdx/tools/shader/core/src/main/java",
                "../../../libfdx/backends/c_shared/src/main/java",
                "../../../libfdx/backends/web/src/main/java",
                "../../../libfdx/backends/desktop_c/src/main/java",
                "../../../libfdx/backends/ios_c/src/main/java",
                "../../../libfdx/backends/psp/src/main/java"
            )
        }
    }
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
