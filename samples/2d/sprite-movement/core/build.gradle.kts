
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

val libfdxDependencyVersion =
    gradle.extensions.extraProperties.get("libfdxDependencyVersion") as String

dependencies {
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        api("${libs.versions.libfdxGroup.get()}:application:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:asset_loaders:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:camera:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:files:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:graphics:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:g2d:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:input:$libfdxDependencyVersion")
    } else {
        api(project(":libfdx:framework:application"))
        implementation(project(":libfdx:framework:assets:loaders"))
        implementation(project(":libfdx:framework:camera"))
        implementation(project(":libfdx:framework:files"))
        implementation(project(":libfdx:framework:graphics"))
        implementation(project(":libfdx:framework:g2d"))
        implementation(project(":libfdx:framework:input"))
    }
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
