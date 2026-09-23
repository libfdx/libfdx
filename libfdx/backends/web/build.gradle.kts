plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

val moduleName = "backend_web"

base {
    archivesName.set(moduleName)
}

dependencies {
    implementation(project(":libfdx:backends:c_shared"))
    implementation(project(":libfdx:tools:font"))

    api(project(":libfdx:framework:fdx:core"))
    api(project(":libfdx:framework:application"))
    api(project(":libfdx:framework:display"))
    api(project(":libfdx:framework:files"))
    api(project(":libfdx:framework:graphics"))
    api(project(":libfdx:framework:assets:loaders"))
    api(project(":libfdx:framework:ui-kit"))
    // The web compiler binds model worker defaults only in applications using G3D.
    compileOnly(project(":libfdx:framework:g3d"))

    runtimeOnly(project(":libfdx:framework:fdx:platform:web"))

    implementation(libs.teavm.jso)
    implementation(libs.teavm.jso.apis)
    implementation(libs.teavm.jso.impl)
    implementation("org.teavm:teavm-platform:${libs.versions.teavm.get()}")
    compileOnly("org.teavm:teavm-core:${libs.versions.teavm.get()}")

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// A separate entry point keeps the worker free of application lifecycle and GPU state.
val workerCompiler = sourceSets.create("workerCompiler")
val workerLibraries = configurations.create("workerLibraries")
dependencies {
    add(workerCompiler.implementationConfigurationName, libs.teavm.tooling)
    add(workerLibraries.name, libs.teavm.classlib)
    add(workerLibraries.name, libs.teavm.jso.impl)
}
val workerResources = layout.buildDirectory.dir("generated/resources/preparationWorker")
val workerInputs = files(sourceSets.main.get().output.classesDirs, sourceSets.main.get().compileClasspath, workerLibraries)
val compilePreparationWorker = tasks.register<JavaExec>("compilePreparationWorker") {
    description = "Compiles Java preparation worker code for embedding as an application string."
    dependsOn(tasks.named("compileJava"), tasks.named(workerCompiler.classesTaskName))
    classpath = workerCompiler.runtimeClasspath
    mainClass.set("io.github.libfdx.backend.web.tooling.PreparationWorkerCompiler")
    inputs.files(workerInputs)
    inputs.files(workerCompiler.runtimeClasspath)
    outputs.dir(workerResources)
    doFirst { args(workerResources.get().asFile.absolutePath, workerInputs.asPath) }
}
tasks.named<ProcessResources>("processResources") {
    dependsOn(compilePreparationWorker)
    from(workerResources)
}

tasks.test {
    useJUnitPlatform()
}
java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = moduleName
            from(components["java"])
        }
    }
}
