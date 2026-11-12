
plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}


base {
    archivesName.set("sample_platformer_core")
}

dependencies {
    api(project(":libfdx:framework:application"))
    implementation(project(":libfdx:framework:graphics"))
    implementation(project(":libfdx:framework:g2d"))
    implementation(project(":libfdx:extensions:maps:tiled"))
    implementation(project(":libfdx:extensions:audio:loaders"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
sourceSets.named("test") { resources.srcDir(layout.projectDirectory.dir("../assets")) }
