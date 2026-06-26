import io.github.libfdx.build.LibExt
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.Sync

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
        implementation("${LibExt.fdxGroup}:graphics:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:net:${LibExt.publishedLibfdxVersion}")
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

// Keep borrowed helpers in this build directory so IntelliJ does not assign libFDX runtime sources to the plugin module.
val borrowedLibfdxSources = layout.buildDirectory.dir("generated/sources/libfdx-borrowed/java")

val syncBorrowedLibfdxSources = tasks.register<Sync>("syncBorrowedLibfdxSources") {
    into(borrowedLibfdxSources)
    from("../../../libfdx/runtime/fdx/core/src/main/java") {
        include("io/github/libfdx/core/FdxException.java")
    }
    from("../../../libfdx/graphics/api/src/main/java") {
        include("io/github/libfdx/graphics/ShaderProfile.java")
        include("io/github/libfdx/graphics/ShaderProfileValidator.java")
        include("io/github/libfdx/graphics/ShaderValidationDiagnostic.java")
        include("io/github/libfdx/graphics/ShaderValidationResult.java")
        include("io/github/libfdx/graphics/ShaderValidationSeverity.java")
    }
    from("../../../libfdx/tools/font/src/main/java") {
        include("io/github/libfdx/tools/font/*.java")
    }
    from("../../../libfdx/backends/c_shared/src/main/java") {
        include("io/github/libfdx/backend/cshared/BuilderException.java")
    }
    from("../../../libfdx/backends/web/src/main/java") {
        include("io/github/libfdx/backend/web/TeaVMAssetProperties.java")
        include("io/github/libfdx/backend/web/WebApp.java")
        include("io/github/libfdx/backend/web/WebAppWriter.java")
        include("io/github/libfdx/backend/web/WebAsset.java")
        include("io/github/libfdx/backend/web/WebAssets.java")
    }
    from("../../../libfdx/backends/desktop_c/src/main/java") {
        include("io/github/libfdx/backend/desktopc/NativeProject.java")
        include("io/github/libfdx/backend/desktopc/NativeProjectWriter.java")
    }
    from("../../../libfdx/backends/ios_c/src/main/java") {
        include("io/github/libfdx/backend/iosc/IosCGraphicsApi.java")
        include("io/github/libfdx/backend/iosc/IosCProject.java")
        include("io/github/libfdx/backend/iosc/IosCProjectWriter.java")
    }
    from("../../../libfdx/backends/psp/src/main/java") {
        include("io/github/libfdx/backend/psp/PspProject.java")
        include("io/github/libfdx/backend/psp/PspProjectWriter.java")
    }
}

if (!LibExt.usePublishedLibfdx) {
    sourceSets {
        main {
            java.srcDir(borrowedLibfdxSources)
        }
    }
    tasks.named("compileKotlin") {
        dependsOn(syncBorrowedLibfdxSources)
    }
    tasks.named("compileJava") {
        dependsOn(syncBorrowedLibfdxSources)
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
