plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

val moduleName = "backend_ios_c"

base {
    archivesName.set(moduleName)
}

dependencies {
    implementation(project(":libfdx:backends:c_shared"))

    api(project(":libfdx:framework:fdx:core"))
    api(project(":libfdx:framework:application"))
    api(project(":libfdx:framework:display"))
    api(project(":libfdx:framework:files"))
    api(project(":libfdx:framework:input"))
    api(project(":libfdx:framework:graphics"))
    api(project(":libfdx:extensions:graphics:gl:core"))
    api(libs.teavm.interop)

    runtimeOnly(project(":libfdx:framework:fdx:platform:shared"))
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
