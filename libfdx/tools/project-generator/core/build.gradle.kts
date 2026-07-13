import io.github.libfdx.build.LibExt

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

group = "${LibExt.fdxGroup}.tools.projectgenerator"

tasks.processResources {
    from(LibExt.rootDirectory.resolve("libfdx.toml")) {
        into("io/github/libfdx/tools/project/generator")
    }
}

tasks.register<JavaExec>("test_generate_project") {
    group = "verification"
    description = "Runs the project generator core smoke checks."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.libfdx.tools.project.generator.ProjectGeneratorSmokeTest")
    systemProperty("libfdx.expectedVersion", LibExt.fdxSnapshotVersion)
}

tasks.named<Test>("test") {
    enabled = false
}

tasks.named("check") {
    dependsOn("test_generate_project")
}
