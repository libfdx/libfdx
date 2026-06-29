plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

val moduleName = "backend_desktop"

base {
    archivesName.set(moduleName)
}

dependencies {
    api(project(":libfdx:framework:fdx:core"))
    implementation(project(":libfdx:framework:math"))
    api(project(":libfdx:framework:assets:manager"))
    api(project(":libfdx:framework:application"))
    api(project(":libfdx:framework:display"))
    api(project(":libfdx:framework:files"))
    api(project(":libfdx:framework:input"))
    api(project(":libfdx:framework:graphics"))
    api(project(":libfdx:extensions:graphics:gl:core"))
    api(project(":libfdx:extensions:graphics:vulkan:core"))

    runtimeOnly(project(":libfdx:framework:fdx:platform:desktop"))

    api(libs.lwjgl)
    api(libs.lwjgl.freetype)
    api(libs.lwjgl.glfw)
    compileOnly(libs.lwjgl.opengl)
    compileOnly(libs.lwjgl.vulkan)

    api(variantOf(libs.lwjgl) { classifier("natives-windows") })
    api(variantOf(libs.lwjgl) { classifier("natives-linux") })
    api(variantOf(libs.lwjgl) { classifier("natives-macos") })
    api(variantOf(libs.lwjgl) { classifier("natives-macos-arm64") })

    api(variantOf(libs.lwjgl.freetype) { classifier("natives-windows") })
    api(variantOf(libs.lwjgl.freetype) { classifier("natives-linux") })
    api(variantOf(libs.lwjgl.freetype) { classifier("natives-macos") })
    api(variantOf(libs.lwjgl.freetype) { classifier("natives-macos-arm64") })

    api(variantOf(libs.lwjgl.glfw) { classifier("natives-windows") })
    api(variantOf(libs.lwjgl.glfw) { classifier("natives-linux") })
    api(variantOf(libs.lwjgl.glfw) { classifier("natives-macos") })
    api(variantOf(libs.lwjgl.glfw) { classifier("natives-macos-arm64") })
}
java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = moduleName
            from(components["java"])
        }
    }
}
