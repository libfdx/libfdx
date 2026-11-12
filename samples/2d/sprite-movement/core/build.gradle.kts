
plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}



base {
    archivesName.set("sample_2d_sprite_movement_core")
}

dependencies {
    api(project(":libfdx:framework:application"))
    implementation(project(":libfdx:framework:assets:loaders"))
    implementation(project(":libfdx:framework:camera"))
    implementation(project(":libfdx:framework:files"))
    implementation(project(":libfdx:framework:graphics"))
    implementation(project(":libfdx:framework:g2d"))
    implementation(project(":libfdx:framework:input"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val sampleRoot = layout.projectDirectory.dir("..")

tasks.processResources {
    from(sampleRoot.dir("assets"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    workingDir = sampleRoot.asFile
}
