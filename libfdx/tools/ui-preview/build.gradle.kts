plugins {
    id("java-library")
    id("maven-publish")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
    withSourcesJar()
    withJavadocJar()
}

base { archivesName.set("tools_ui_preview") }

dependencies {
    api(project(":libfdx:framework:ui-kit"))
    api(project(":libfdx:framework:assets:manager"))
    implementation(project(":libfdx:backends:desktop"))
    runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    systemProperty("preview.testClasses", sourceSets.test.get().output.classesDirs.asPath)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "tools_ui_preview"
            from(components["java"])
        }
    }
}
