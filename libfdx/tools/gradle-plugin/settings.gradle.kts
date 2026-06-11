pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../../gradle/libs.versions.toml"))
        }
    }
}

fun includeLibfdxProject(path: String, relativePath: String) {
    include(path)
    project(path).projectDir = file(relativePath)
}

includeLibfdxProject(":libfdx:foundation:math", "../../foundation/math")
includeLibfdxProject(":libfdx:runtime:application", "../../runtime/application")
includeLibfdxProject(":libfdx:runtime:fdx:core", "../../runtime/fdx/core")
includeLibfdxProject(":libfdx:runtime:fdx:platform:shared", "../../runtime/fdx/platform/shared")
includeLibfdxProject(":libfdx:runtime:fdx:platform:web", "../../runtime/fdx/platform/web")
includeLibfdxProject(":libfdx:runtime:display", "../../runtime/display")
includeLibfdxProject(":libfdx:runtime:files", "../../runtime/files")
includeLibfdxProject(":libfdx:runtime:input", "../../runtime/input")
includeLibfdxProject(":libfdx:graphics:api", "../../graphics/api")
includeLibfdxProject(":libfdx:extensions:graphics:gl:core", "../../extensions/graphics/gl/core")
includeLibfdxProject(":libfdx:extensions:graphics:vulkan:core", "../../extensions/graphics/vulkan/core")
includeLibfdxProject(":libfdx:tools:font", "../font")
includeLibfdxProject(":libfdx:tools:shader", "../shader")
includeLibfdxProject(":libfdx:backends:teavm_shared", "../../backends/teavm_shared")
includeLibfdxProject(":libfdx:backends:web", "../../backends/web")
includeLibfdxProject(":libfdx:backends:desktop_native", "../../backends/desktop_native")
includeLibfdxProject(":libfdx:backends:psp", "../../backends/psp")

project(":libfdx").projectDir = file("../..")
project(":libfdx:foundation").projectDir = file("../../foundation")
project(":libfdx:runtime").projectDir = file("../../runtime")
project(":libfdx:runtime:fdx").projectDir = file("../../runtime/fdx")
project(":libfdx:runtime:fdx:platform").projectDir = file("../../runtime/fdx/platform")
project(":libfdx:graphics").projectDir = file("../../graphics")
project(":libfdx:extensions").projectDir = file("../../extensions")
project(":libfdx:extensions:graphics").projectDir = file("../../extensions/graphics")
project(":libfdx:extensions:graphics:gl").projectDir = file("../../extensions/graphics/gl")
project(":libfdx:extensions:graphics:vulkan").projectDir = file("../../extensions/graphics/vulkan")
project(":libfdx:tools").projectDir = file("..")
project(":libfdx:backends").projectDir = file("../../backends")
