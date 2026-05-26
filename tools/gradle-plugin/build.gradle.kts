plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "io.github.libfdx"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven {
        url = uri("http://teavm.org/maven/repository/")
        isAllowInsecureProtocol = true
    }
}

dependencies {
    implementation(libs.teavm.gradle.plugin)
}

sourceSets {
    main {
        java {
            srcDir("../../libfdx/tools/font/src/main/java")
            srcDir("../../libfdx/backends/teavm_shared/src/main/java")
            srcDir("../../libfdx/backends/web/src/main/java")
            srcDir("../../libfdx/backends/desktop_native/src/main/java")
            srcDir("../../libfdx/backends/psp/src/main/java")
            include("io/github/libfdx/tools/font/BitmapFontGenerator.java")
            include("io/github/libfdx/tools/font/BitmapFontResult.java")
            include("io/github/libfdx/tools/font/BitmapFontSpec.java")
            include("io/github/libfdx/backend/teavm/shared/BuilderException.java")
            include("io/github/libfdx/backend/web/TeaVMAssetProperties.java")
            include("io/github/libfdx/backend/web/WebApp.java")
            include("io/github/libfdx/backend/web/WebAppWriter.java")
            include("io/github/libfdx/backend/web/WebAsset.java")
            include("io/github/libfdx/backend/web/WebAssets.java")
            include("io/github/libfdx/backend/desktopnative/NativeProject.java")
            include("io/github/libfdx/backend/desktopnative/NativeProjectWriter.java")
            include("io/github/libfdx/backend/psp/PspProject.java")
            include("io/github/libfdx/backend/psp/PspProjectWriter.java")
        }
    }
}

gradlePlugin {
    plugins {
        create("libfdx") {
            id = "io.github.libfdx"
            implementationClass = "io.github.libfdx.gradle.LibfdxGradlePlugin"
        }
    }
}
