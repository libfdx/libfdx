
plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}


val moduleName = "wgpu_web"

base {
    archivesName.set(moduleName)
}

dependencies {
    api(project(":libfdx:extensions:graphics:wgpu:core"))

    implementation(libs.jwebgpu.web)
    implementation(libs.jwebgpu.web.wasm)
    implementation(libs.jmultiplatform)
    implementation(libs.teavm.extension.spi)
    implementation(libs.teavm.jso)
    implementation(libs.teavm.jso.apis)
    implementation(libs.teavm.jso.impl)
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
