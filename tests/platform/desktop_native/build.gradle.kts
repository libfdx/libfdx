plugins {
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("tests_desktop_native")
}

// TeaVM uses main; jNative gets its own compiler and native-resource classpath.
val cppSourceSet = sourceSets.create("cpp")
val nativeSupportSourceSet = sourceSets.create("nativeSupport")
val generatedCProcessResources = layout.buildDirectory.dir("generated/resources/testProcessC")
val generatedCppProcessResources = layout.buildDirectory.dir("generated/resources/testProcessCpp")
val copyCProcessHeader = tasks.register<Copy>("copyCProcessHeader") {
    from("src/nativeSupport/c/libfdx_test_process.h")
    into(generatedCProcessResources.map { it.dir("libfdx-native/desktop/desktop_c") })
}
val copyCppProcessHeader = tasks.register<Copy>("copyCppProcessHeader") {
    from("src/nativeSupport/c/libfdx_test_process.h")
    into(generatedCppProcessResources.map { it.dir("libfdx-native/desktop/desktop_cpp") })
}
sourceSets.main { resources.srcDir(generatedCProcessResources) }
cppSourceSet.resources.srcDir(generatedCppProcessResources)
tasks.named("processResources") { dependsOn(copyCProcessHeader) }
tasks.named(cppSourceSet.processResourcesTaskName) { dependsOn(copyCppProcessHeader) }
tasks.named<Jar>("jar") {
    from(cppSourceSet.output)
    from(nativeSupportSourceSet.output)
}

dependencies {
    add(nativeSupportSourceSet.implementationConfigurationName, project(":tests:core"))
    implementation(nativeSupportSourceSet.output)
    add(cppSourceSet.implementationConfigurationName, nativeSupportSourceSet.output)
    implementation(project(":tests:core"))
    if (gradle.extensions.extraProperties.get("libfdxUsePublishedLibfdx") as Boolean) {
        implementation("${libs.versions.libfdxGroup.get()}:backend_desktop_c:${libs.versions.libfdxSnapshot.get()}")
        runtimeOnly("${libs.versions.libfdxGroup.get()}:gl_desktop_c:${libs.versions.libfdxSnapshot.get()}")
        runtimeOnly("${libs.versions.libfdxGroup.get()}:vulkan_desktop_c:${libs.versions.libfdxSnapshot.get()}")
    } else {
        implementation(project(":libfdx:backends:desktop_c"))
        runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop_c"))
        runtimeOnly(project(":libfdx:extensions:graphics:vulkan:platform:desktop_c"))
    }
    add(cppSourceSet.implementationConfigurationName, project(":tests:core"))
    add(cppSourceSet.implementationConfigurationName, project(":libfdx:backends:desktop_cpp"))
    add(cppSourceSet.runtimeOnlyConfigurationName, project(":libfdx:extensions:graphics:gl:platform:desktop_cpp"))
    add(cppSourceSet.runtimeOnlyConfigurationName, project(":libfdx:extensions:graphics:vulkan:platform:desktop_cpp"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

val nativeFrames = providers.gradleProperty("nativeFrames").orElse("0")
val nativeTest = providers.gradleProperty("nativeTest").orElse("selector")
val nativeBuildJobs = providers.gradleProperty("nativeBuildJobs")
    .map(String::toInt)
    .orElse(minOf(8, Runtime.getRuntime().availableProcessors()))

libfdx {
    assets(rootProject.layout.projectDirectory.dir("tests/assets"))
    desktopC {
        minHeapSize.set(64)
        maxHeapSize.set(1024)
        obfuscated.set(false)
        fastGlobalAnalysis.set(false)
        shortFileNames.set(true)

        target("opengl") {
            displayName.set("desktop C OpenGL graphics tests")
            mainClass.set("io.github.libfdx.tests.desktopc.DesktopCOpenGLTestLauncher")
            targetFileName.set("libfdx-tests-opengl-desktop-c")
        }
        target("vulkan") {
            displayName.set("desktop C Vulkan graphics tests")
            mainClass.set("io.github.libfdx.tests.desktopc.DesktopCVulkanTestLauncher")
            targetFileName.set("libfdx-tests-vulkan-desktop-c")
        }
    }
    desktopCPP {
        sourceSet.set(cppSourceSet.name)
        // Keep test builds optimized per file without the full-program optimization link.
        cmakeArguments.add("-DJNATIVE_IPO=OFF")
        cmakeArguments.add("-DJNATIVE_MSVC_AGGRESSIVE_INLINING=OFF")
        cmakeArguments.add(nativeBuildJobs.map { jobs ->
            require(jobs > 0) { "nativeBuildJobs must be positive" }
            "-DJNATIVE_MSVC_COMPILE_JOBS=$jobs"
        })
        cmakeBuildArguments.addAll(nativeBuildJobs.map { jobs -> listOf("--parallel", jobs.toString()) })
        // Keep generated C++ object paths within jNative's portable path budget.
        outputDir.set(rootProject.layout.buildDirectory.dir("tests-cpp"))
        val testProperties = System.getProperties().stringPropertyNames()
            .filter { it.startsWith("libfdx.test.") }
            .sorted()
            .map { "-D$it=${System.getProperty(it)}" }
        target("opengl") {
            displayName.set("desktop C++ OpenGL graphics tests")
            mainClass.set("io.github.libfdx.tests.desktopcpp.DesktopCPPOpenGLTestLauncher")
            targetFileName.set("libfdx-tests-opengl-desktop-cpp")
            runArgs.set(listOf(nativeTest.get(), nativeFrames.get()) + testProperties)
        }
        target("vulkan") {
            displayName.set("desktop C++ Vulkan graphics tests")
            mainClass.set("io.github.libfdx.tests.desktopcpp.DesktopCPPVulkanTestLauncher")
            targetFileName.set("libfdx-tests-vulkan-desktop-cpp")
            runArgs.set(listOf(nativeTest.get(), nativeFrames.get()) + testProperties)
        }
    }
}
