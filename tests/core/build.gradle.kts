
plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}



base {
    archivesName.set("tests_core")
}

dependencies {
    api(project(":libfdx:framework:application"))
    api(project(":libfdx:extensions:audio:loaders"))
    api(project(":libfdx:framework:input"))
    api(project(":libfdx:framework:graphics"))
    api(project(":libfdx:framework:camera"))
    api(project(":libfdx:framework:g2d"))
    api(project(":libfdx:extensions:maps:tiled"))
    api(project(":libfdx:extensions:maps:streaming"))
    api(project(":libfdx:extensions:graphics:effects"))
    api(project(":libfdx:framework:g3d"))
    api(project(":libfdx:extensions:graphics:meshoptimizer:core"))
    api(project(":libfdx:extensions:graphics:shader-graph:g2d"))
    api(project(":libfdx:extensions:graphics:shader-graph:g3d"))
    api(project(":libfdx:framework:ui-kit"))
    api(project(":libfdx:extensions:scenario_validator:core"))
    api(project(":libfdx:extensions:scenario_validator:ui-kit"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

val particleCaptureDirectory = providers.gradleProperty("libfdxParticleCaptures")
    .orElse(rootProject.layout.projectDirectory.dir("tests/platform/desktop/build/captures/particle-occlusion").asFile.absolutePath)

val particleProvider = providers.gradleProperty("libfdxParticleProvider").orElse("gl")
tasks.register<JavaExec>("check_particle_solids") {
    group = "verification"
    description = "Checks solid particles before, within and behind fire/smoke, including reversed cameras and submission order."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.libfdx.tests.graphics.ParticleSolidOcclusionCheck")
    doFirst { args(particleCaptureDirectory.get(), particleProvider.get()) }
}

tasks.register<JavaExec>("check_particle_occlusion") {
    group = "verification"
    description = "Checks captured fire/smoke occlusion from both camera directions and reversed grid arguments."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.libfdx.tests.graphics.ParticleOcclusionCheck")
    doFirst { args(particleCaptureDirectory.get()) }
}
