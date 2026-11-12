plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("sample_base_starter_project_core")
}

dependencies {
    api(project(":libfdx:framework:application"))
    implementation(project(":libfdx:framework:g2d"))
}

val sampleRoot = layout.projectDirectory.dir("..")

tasks.processResources {
    from(sampleRoot.dir("assets"))
}
