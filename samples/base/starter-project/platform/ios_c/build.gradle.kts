plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("sample_base_starter_project_ios_c")
}

val sampleProjectPath = project.path.substringBefore(":platform:")
val libfdxDependencyVersion =
    gradle.extensions.extraProperties.get("libfdxDependencyVersion") as String

dependencies {
    implementation(project("$sampleProjectPath:core"))
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_ios_c:$libfdxDependencyVersion")
    } else {
        implementation(project(":libfdx:backends:ios_c"))
    }
}

tasks.register("starter_project_ios_c_gles_generate") {
    group = "application"
    description = "Generates the Starter Project iOS C GLES TeaVM and Xcode project."
    dependsOn("$sampleProjectPath:platform:plugin:libfdx_ios_c_gles_generate")
}

tasks.register("starter_project_ios_c_metal_generate") {
    group = "application"
    description = "Generates the Starter Project iOS C Metal TeaVM and Xcode project."
    dependsOn("$sampleProjectPath:platform:plugin:libfdx_ios_c_metal_generate")
}
