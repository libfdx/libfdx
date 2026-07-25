
plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("project_generator_core")
}


tasks.processResources {
    from(rootProject.layout.projectDirectory.file("gradle/libs.versions.toml")) {
        into("io/github/libfdx/tools/project/generator")
    }
}

tasks.register<JavaExec>("test_generate_project") {
    group = "verification"
    description = "Runs the project generator core smoke checks."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.libfdx.tools.project.generator.ProjectGeneratorSmokeTest")
    val expectedVersion = if (libs.versions.libfdxSnapshot.get().startsWith("-")) {
        libs.versions.libfdxRelease.get() + libs.versions.libfdxSnapshot.get()
    } else {
        libs.versions.libfdxSnapshot.get()
    }
    systemProperty("libfdx.expectedVersion", expectedVersion)
}

tasks.named<Test>("test") {
    enabled = false
}

tasks.named("check") {
    dependsOn("test_generate_project")
}
