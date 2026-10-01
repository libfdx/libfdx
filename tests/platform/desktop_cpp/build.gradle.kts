plugins {
    java
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("tests_desktop_cpp")
}

dependencies {
    implementation(project(":libfdx:backends:desktop_cpp"))
    implementation(project(":tests:core"))
    runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop_cpp"))
    runtimeOnly(project(":libfdx:extensions:graphics:vulkan:platform:desktop_cpp"))
}

val nativeBuildType = providers.gradleProperty("nativeBuildType").orElse("DEBUG")
val nativeFrames = providers.gradleProperty("nativeFrames").orElse("0")
val nativeTest = providers.gradleProperty("nativeTest").orElse("selector")
val nativeGenerator = providers.gradleProperty("nativeGenerator").orElse("")

val prepareNativeAssets = tasks.register<Sync>("prepare_native_assets") {
    group = "libfdx desktop cpp"
    description = "Stage test assets and framework resources for the native test executable."
    dependsOn(configurations.runtimeClasspath)
    duplicatesStrategy = DuplicatesStrategy.FAIL
    from(rootProject.layout.projectDirectory.dir("tests/assets"))
    from(providers.provider {
        configurations.runtimeClasspath.get().files.map { dependency ->
            if (dependency.isDirectory) dependency else zipTree(dependency)
        }
    }) {
        include("libfdx-assets/**")
    }
    into(layout.buildDirectory.dir("assets"))
}

tasks.withType<JavaExec>().configureEach {
    dependsOn(":libfdx:framework:fdx:platform:desktop:generate_runtime_fdx_host_native")
    dependsOn(prepareNativeAssets)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.libfdx.testsupport.desktopcpp.BuildTests")
    workingDir(projectDir)
    systemProperty("libfdx.native.buildRoot", layout.buildDirectory.get().asFile.absolutePath)
    systemProperty("libfdx.native.buildType", nativeBuildType.get())
    systemProperty("libfdx.native.generator", nativeGenerator.get())
    systemProperty("libfdx.native.test", nativeTest.get())
    systemProperty("libfdx.native.assets", layout.buildDirectory.dir("assets").get().asFile.absolutePath)
    systemProperties(System.getProperties().stringPropertyNames()
        .filter { it.startsWith("libfdx.test.") }
        .associateWith { System.getProperty(it) })
}

tasks.register<JavaExec>("generate_native") {
    group = "libfdx desktop cpp"
    description = "Translate the shared test suite and desktop backend to C++."
    args("generate")
}

tasks.register<JavaExec>("build_native") {
    group = "libfdx desktop cpp"
    description = "Translate and compile the shared native test suite."
    args("build")
}

tasks.register<JavaExec>("run_opengl") {
    group = "libfdx desktop cpp"
    description = "Run the OpenGL test chooser; select a test with nativeTest."
    args("run", "gl", nativeFrames.get())
}

tasks.register<JavaExec>("run_vulkan") {
    group = "libfdx desktop cpp"
    description = "Run the Vulkan test chooser; select a test with nativeTest."
    args("run", "vulkan", nativeFrames.get())
}

tasks.register<JavaExec>("verify_native") {
    group = "verification"
    description = "Build release by default, then run the full automatic suite on OpenGL and Vulkan."
    systemProperty("libfdx.native.buildType", providers.gradleProperty("nativeBuildType").orElse("RELEASE").get())
    args("verify")
}

val buildPerformance = tasks.register<JavaExec>("build_performance") {
    group = "verification"
    description = "Build the release C++ executable for performance verification."
    systemProperty("libfdx.native.buildType", "RELEASE")
    args("build")
}

tasks.register<JavaExec>("verify_performance") {
    group = "verification"
    description = "Compare release SpriteBatchStressTest FPS with desktop_c on uncapped OpenGL."
    dependsOn(buildPerformance)
    dependsOn(":tests:platform:desktop_c:libfdx_desktop_c_opengl_build_release")
    mainClass.set("io.github.libfdx.testsupport.desktopcpp.NativePerformanceComparison")
    val executableSuffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
    args(layout.buildDirectory.file("native/release/libfdx-tests-desktop-cpp$executableSuffix").get().asFile.absolutePath)
    args(project(":tests:platform:desktop_c").layout.buildDirectory
        .file("dist/desktop-c/c/release/libfdx-tests-opengl-desktop-c_release$executableSuffix").get().asFile.absolutePath)
    args(layout.buildDirectory.dir("assets").get().asFile.absolutePath)
    args(layout.buildDirectory.dir("reports/performance").get().asFile.absolutePath)
    systemProperties(System.getProperties().stringPropertyNames()
        .filter { it.startsWith("performance.") }
        .associateWith { System.getProperty(it) })
}

tasks.register<JavaExec>("verify_performance_suite") {
    group = "verification"
    description = "Compare every registered test on GL/Vulkan; build both desktop_c references in separate invocations first."
    dependsOn(buildPerformance)
    mainClass.set("io.github.libfdx.testsupport.desktopcpp.NativePerformanceSuite")
    val executableSuffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
    val cBuild = project(":tests:platform:desktop_c").layout.buildDirectory
    args(layout.buildDirectory.file("native/release/libfdx-tests-desktop-cpp$executableSuffix").get().asFile.absolutePath)
    args(cBuild.file("dist/desktop-c/c/release/libfdx-tests-opengl-desktop-c_release$executableSuffix").get().asFile.absolutePath)
    args(cBuild.file("dist/desktop-c/c/release/libfdx-tests-vulkan-desktop-c_release$executableSuffix").get().asFile.absolutePath)
    args(layout.buildDirectory.dir("assets").get().asFile.absolutePath)
    args(layout.buildDirectory.dir("reports/performance-suite").get().asFile.absolutePath)
    systemProperties(System.getProperties().stringPropertyNames()
        .filter { it.startsWith("performance.") }
        .associateWith { System.getProperty(it) })
}
