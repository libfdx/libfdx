import io.github.libfdx.build.LibExt
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

plugins {
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

extra["libfdxPublishTarget"] = "GRADLE_PLUGIN"
extra["libfdxGradlePluginDependencyArtifacts"] = libfdxGradlePluginDependencyArtifacts
apply(from = "../../../buildSrc/src/main/kotlin/publish.gradle.kts")

fun libfdxSourceTree(path: String, vararg includes: String) =
    fileTree("../../../$path/src/main/java") {
        includes.forEach { include(it) }
    }

val localLibfdxPluginDependencyClasses =
    layout.buildDirectory.dir("generated/classes/local-libfdx-plugin-dependencies")

val compileLocalLibfdxPluginDependencies = tasks.register<JavaCompile>("compileLocalLibfdxPluginDependencies") {
    sourceCompatibility = JavaVersion.toVersion(25).toString()
    targetCompatibility = JavaVersion.toVersion(25).toString()
    options.encoding = "UTF-8"
    classpath = files()
    destinationDirectory.set(localLibfdxPluginDependencyClasses)
    source(
        libfdxSourceTree(
            "libfdx/runtime/fdx/core",
            "io/github/libfdx/core/FdxException.java"
        ),
        libfdxSourceTree(
            "libfdx/graphics/api",
            "io/github/libfdx/graphics/ShaderProfile.java",
            "io/github/libfdx/graphics/ShaderProfileValidator.java",
            "io/github/libfdx/graphics/ShaderValidationDiagnostic.java",
            "io/github/libfdx/graphics/ShaderValidationResult.java",
            "io/github/libfdx/graphics/ShaderValidationSeverity.java"
        ),
        libfdxSourceTree(
            "libfdx/tools/font",
            "io/github/libfdx/tools/font/*.java"
        ),
        libfdxSourceTree(
            "libfdx/backends/c_shared",
            "io/github/libfdx/backend/cshared/BuilderException.java"
        ),
        libfdxSourceTree(
            "libfdx/backends/web",
            "io/github/libfdx/backend/web/TeaVMAssetProperties.java",
            "io/github/libfdx/backend/web/WebApp.java",
            "io/github/libfdx/backend/web/WebAppWriter.java",
            "io/github/libfdx/backend/web/WebAsset.java",
            "io/github/libfdx/backend/web/WebAssets.java"
        ),
        libfdxSourceTree(
            "libfdx/backends/desktop_c",
            "io/github/libfdx/backend/desktopc/NativeProject.java",
            "io/github/libfdx/backend/desktopc/NativeProjectWriter.java"
        ),
        libfdxSourceTree(
            "libfdx/backends/ios_c",
            "io/github/libfdx/backend/iosc/IosCGraphicsApi.java",
            "io/github/libfdx/backend/iosc/IosCProject.java",
            "io/github/libfdx/backend/iosc/IosCProjectWriter.java"
        ),
        libfdxSourceTree(
            "libfdx/backends/psp",
            "io/github/libfdx/backend/psp/PspProject.java",
            "io/github/libfdx/backend/psp/PspProjectWriter.java"
        )
    )
}

val localLibfdxPluginDependencyJar = tasks.register<Jar>("localLibfdxPluginDependencyJar") {
    archiveBaseName.set("libfdx-gradle-plugin-local-dependencies")
    destinationDirectory.set(layout.buildDirectory.dir("local-dependencies"))
    dependsOn(compileLocalLibfdxPluginDependencies)
    from(localLibfdxPluginDependencyClasses)
}

dependencies {
    implementation(libs.teavm.gradle.plugin)
    if (LibExt.usePublishedLibfdx) {
        libfdxGradlePluginDependencyArtifacts.forEach { artifact ->
            implementation("${LibExt.fdxGroup}:$artifact:${LibExt.publishedLibfdxVersion}")
        }
    } else {
        implementation(files(localLibfdxPluginDependencyJar))
        implementation(libs.teavm.tooling)
        implementation(libs.teavm.classlib)
        implementation(libs.teavm.interop)
        implementation(libs.teavm.jso)
        implementation(libs.teavm.jso.apis)
        implementation(libs.teavm.jso.impl)
        implementation("org.teavm:teavm-platform:${libs.versions.teavm.get()}")
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
