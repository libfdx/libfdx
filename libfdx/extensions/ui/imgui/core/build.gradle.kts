plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

val moduleName = "imgui_ext"

base {
    archivesName.set(moduleName)
}

dependencies {
    compileOnlyApi(libs.jimgui.core)
    api(project(":libfdx:framework:fdx:core"))
    api(project(":libfdx:framework:application"))
    api(project(":libfdx:framework:display"))
    api(project(":libfdx:framework:input"))
    api(project(":libfdx:framework:graphics"))
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
