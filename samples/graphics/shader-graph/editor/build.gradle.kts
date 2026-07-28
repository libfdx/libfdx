plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("sample_shader_graph_editor")
}

val sampleProjectPath = project.path.substringBeforeLast(":editor")
val libfdxDependencyVersion =
    gradle.extensions.extraProperties.get("libfdxDependencyVersion") as String

dependencies {
    api(project("$sampleProjectPath:core"))
    if ((gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean)) {
        implementation("${libs.versions.libfdxGroup.get()}:application:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:files:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:graphics:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:shader_graph_ui_kit:$libfdxDependencyVersion")
        implementation("${libs.versions.libfdxGroup.get()}:ui_kit:$libfdxDependencyVersion")
    } else {
        implementation(project(":libfdx:framework:application"))
        implementation(project(":libfdx:framework:files"))
        implementation(project(":libfdx:framework:graphics"))
        implementation(project(":libfdx:extensions:graphics:shader-graph:ui-kit"))
        implementation(project(":libfdx:framework:ui-kit"))
    }
}
