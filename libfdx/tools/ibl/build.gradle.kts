plugins {
    id("java-library")
    id("application")
    id("maven-publish")
}
java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
    withSourcesJar()
    withJavadocJar()
}
base { archivesName.set("tools_ibl") }
dependencies {
    api(project(":libfdx:framework:g3d"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
application { mainClass.set("io.github.libfdx.tools.ibl.IblMain") }
tasks.named<JavaExec>("run") { workingDir(rootProject.projectDir) }
tasks.named<Test>("test") { useJUnitPlatform() }
publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "tools_ibl"
            from(components["java"])
        }
    }
}
