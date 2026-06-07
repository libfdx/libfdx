plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("backend_desktop")
}

dependencies {
    api(project(":libfdx:runtime:fdx:core"))
    implementation(project(":libfdx:foundation:math"))
    api(project(":libfdx:assets:manager"))
    api(project(":libfdx:runtime:application"))
    api(project(":libfdx:runtime:display"))
    api(project(":libfdx:runtime:files"))
    api(project(":libfdx:runtime:input"))
    api(project(":libfdx:graphics:api"))
    api(project(":libfdx:extensions:graphics:gl:core"))
    api(project(":libfdx:extensions:graphics:vulkan:core"))

    runtimeOnly(project(":libfdx:runtime:fdx:platform:desktop"))

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
