
plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("project_generator_ui")
}


dependencies {
    api(project(":libfdx:tools:project-generator:core"))
    api(project(":libfdx:framework:application"))
    implementation(project(":libfdx:framework:ui-kit"))
}
