package io.github.libfdx.gradle

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class DesktopCppTaskWiringTest {
    private fun project(): ProjectInternal {
        val root = Path.of("build/tmp/desktop-cpp-wiring").toAbsolutePath()
        Files.createDirectories(root)
        val project = ProjectBuilder.builder()
            .withProjectDir(Files.createTempDirectory(root, "project-").toFile())
            .build() as ProjectInternal
        project.pluginManager.apply(LibfdxGradlePlugin::class.java)
        project.dependencies.add("libfdxDesktopCppToolClasspath", project.files("backend.jar"))
        return project
    }

    @Test
    fun `unnamed target wires generation compilation and execution without TeaVM C`() {
        val project = project()
        val extension = project.extensions.getByType(LibfdxExtension::class.java)
        extension.desktopCPP {
            mainClass.set("example.Main")
            targetFileName.set("game")
            runArgs.set(listOf("argument with spaces", "--verify"))
        }
        project.evaluate()

        val generate = project.tasks.getByName("libfdx_desktop_cpp_generate_debug") as LibfdxDesktopCppGenerateTask
        val build = project.tasks.getByName("libfdx_desktop_cpp_build_debug") as LibfdxDesktopCppBuildTask
        val run = project.tasks.getByName("libfdx_desktop_cpp_run_debug") as LibfdxDesktopCppRunTask
        assertEquals("example.Main", generate.mainClass.get())
        assertEquals("game", run.targetFileName.get())
        assertTrue(generate.debugInformation.get())
        assertEquals(project.file("build/dist/desktop-cpp/debug/native"), generate.nativeProjectDir.get().asFile)
        assertEquals(generate.nativeProjectDir.get(), build.projectDir.get())
        assertEquals(project.file("build/dist/desktop-cpp/debug/native/debug"), run.releaseDir.get().asFile)
        assertEquals(listOf("argument with spaces", "--verify"), run.runArgs.get())
        assertTrue(build.taskDependencies.getDependencies(build).contains(generate))
        assertTrue(run.taskDependencies.getDependencies(run).contains(build))
        assertFalse(project.tasks.names.contains("libfdx_desktop_c_generate"))
        assertFalse(generate.dependsOn.any { it.toString().contains("generateC") })
    }

    @Test
    fun `named OpenGL release output stays in the owning module build directory`() {
        val root = project()
        val moduleDir = root.file("modules/desktop")
        Files.createDirectories(moduleDir.toPath())
        val module = ProjectBuilder.builder()
            .withName("desktop")
            .withParent(root)
            .withProjectDir(moduleDir)
            .build() as ProjectInternal
        module.pluginManager.apply(LibfdxGradlePlugin::class.java)
        module.dependencies.add("libfdxDesktopCppToolClasspath", module.files("backend.jar"))
        val extension = module.extensions.getByType(LibfdxExtension::class.java)
        extension.desktopCPP {
            target("opengl") {
                mainClass.set("example.OpenGLMain")
                targetFileName.set("opengl-game")
            }
        }
        module.evaluate()

        val generate = module.tasks.getByName("libfdx_desktop_cpp_opengl_generate_release") as LibfdxDesktopCppGenerateTask
        val build = module.tasks.getByName("libfdx_desktop_cpp_opengl_build_release") as LibfdxDesktopCppBuildTask
        val run = module.tasks.getByName("libfdx_desktop_cpp_opengl_run_release") as LibfdxDesktopCppRunTask
        val assets = module.tasks.getByName("libfdx_desktop_cpp_opengl_assets_release_internal") as LibfdxDesktopCppAssetsTask
        val output = module.layout.buildDirectory.dir("dist/desktop-cpp/opengl/release").get()
        assertEquals(output, generate.buildRoot.get())
        assertEquals(output.dir("native"), generate.nativeProjectDir.get())
        assertEquals(generate.nativeProjectDir.get(), build.projectDir.get())
        assertEquals(output.dir("native/release"), build.releaseDir.get())
        assertEquals(build.releaseDir.get(), run.releaseDir.get())
        assertEquals(run.releaseDir.dir("assets").get(), assets.outputDir.get())
        assertNotEquals(root.layout.buildDirectory.dir("dist/desktop-cpp/opengl/release").get(), generate.buildRoot.get())
        assertTrue(build.taskDependencies.getDependencies(build).contains(generate))
        assertTrue(run.taskDependencies.getDependencies(run).contains(build))
    }

    @Test
    fun `named targets and build types have independent entry points arguments and outputs`() {
        val project = project()
        val extension = project.extensions.getByType(LibfdxExtension::class.java)
        extension.desktopC { mainClass.set("example.CMain") }
        extension.desktopCPP {
            mainClass.set("example.SharedMain")
            outputDir.set(project.layout.buildDirectory.dir("short"))
            runArgs.set(listOf("fallback"))
            target("opengl") { runArgs.set(listOf("gl")) }
            target("vulkan") {
                mainClass.set("example.VulkanMain")
                targetFileName.set("vulkan-game")
            }
        }
        project.evaluate()

        val gl = project.tasks.getByName("libfdx_desktop_cpp_opengl_generate_debug") as LibfdxDesktopCppGenerateTask
        val vk = project.tasks.getByName("libfdx_desktop_cpp_vulkan_generate_debug") as LibfdxDesktopCppGenerateTask
        val release = project.tasks.getByName("libfdx_desktop_cpp_opengl_generate_release") as LibfdxDesktopCppGenerateTask
        val glRun = project.tasks.getByName("libfdx_desktop_cpp_opengl_run_debug") as LibfdxDesktopCppRunTask
        val vkRun = project.tasks.getByName("libfdx_desktop_cpp_vulkan_run_release") as LibfdxDesktopCppRunTask
        assertEquals("example.SharedMain", gl.mainClass.get())
        assertEquals("example.VulkanMain", vk.mainClass.get())
        assertEquals(project.file("build/short/opengl/debug"), gl.buildRoot.get().asFile)
        assertEquals("vulkan-game", vk.targetFileName.get())
        assertEquals(listOf("gl"), glRun.runArgs.get())
        assertEquals(listOf("fallback"), vkRun.runArgs.get())
        assertNotEquals(gl.buildRoot.get(), vk.buildRoot.get())
        assertNotEquals(gl.buildRoot.get(), release.buildRoot.get())
        assertTrue(gl.debugInformation.get())
        assertFalse(release.debugInformation.get())
        assertTrue(project.tasks.names.contains("libfdx_desktop_c_generate"))
        assertFalse(project.tasks.names.contains("libfdx_desktop_cpp_generate_debug"))
    }

    @Test
    fun `C and Cpp source sets keep compiler inputs assets and build dependencies isolated`() {
        val project = project()
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val main = sourceSets.getByName("main")
        val cpp = sourceSets.create("cpp")
        project.dependencies.add(main.runtimeOnlyConfigurationName, project.files("c-runtime.jar"))
        project.dependencies.add(cpp.runtimeOnlyConfigurationName, project.files("cpp-runtime.jar"))
        val extension = project.extensions.getByType(LibfdxExtension::class.java)
        extension.desktopC { mainClass.set("example.CMain") }
        extension.desktopCPP {
            sourceSet.set(cpp.name)
            mainClass.set("example.CppMain")
        }
        project.evaluate()

        val cProject = project.tasks.getByName("libfdx_desktop_c_generate") as LibfdxDesktopCProjectTask
        val cppGenerate = project.tasks.getByName("libfdx_desktop_cpp_generate_debug") as LibfdxDesktopCppGenerateTask
        val cppAssets = project.tasks.getByName("libfdx_desktop_cpp_assets_debug_internal") as LibfdxDesktopCppAssetsTask
        assertEquals(main.runtimeClasspath.files, cProject.nativeResourceClasspath.files)
        assertEquals(cpp.runtimeClasspath.files, cppGenerate.applicationClasspath.files)
        assertEquals(cpp.runtimeClasspath.files, cppAssets.applicationClasspath.files)
        assertFalse(cProject.nativeResourceClasspath.contains(project.file("cpp-runtime.jar")))
        assertFalse(cppGenerate.applicationClasspath.contains(project.file("c-runtime.jar")))
        assertFalse(cppGenerate.applicationClasspath.files.any { it in main.output.classesDirs.files })
        val dependencies = cppGenerate.taskDependencies.getDependencies(cppGenerate).map { it.name }
        assertTrue(dependencies.contains(cpp.classesTaskName))
        assertFalse(dependencies.contains(main.classesTaskName))
    }

    @Test
    fun `native compiler outputs do not become generation outputs or compilation inputs`() {
        val project = project()
        val root = project.file("native").toPath()
        listOf("src/Main.cpp", "CMakeLists.txt", "b/debug/compiler.obj", "debug/game.exe",
            "release/assets/test.txt", "configure-123.log", "compile-456.log")
            .forEach { name ->
                val file = root.resolve(name)
                Files.createDirectories(file.parent)
                Files.writeString(file, name)
            }
        val directory = project.objects.directoryProperty().fileValue(root.toFile())

        assertEquals(
            setOf(root.resolve("src/Main.cpp").toFile(), root.resolve("CMakeLists.txt").toFile()),
            desktopCppProjectFiles(directory).files
        )
    }

    @Test
    fun `staging removes stale assets and preserves application overrides of shared jar assets`() {
        val project = project()
        val root = project.projectDir.toPath()
        val application = root.resolve("assets/libfdx-assets/font.txt")
        Files.createDirectories(application.parent)
        Files.writeString(application, "application")
        val dependency = root.resolve("shared.jar")
        JarOutputStream(Files.newOutputStream(dependency)).use { jar ->
            listOf("font.txt", "license.txt").forEach { name ->
                jar.putNextEntry(JarEntry("libfdx-assets/$name"))
                jar.write("shared".toByteArray())
                jar.closeEntry()
            }
        }
        val output = root.resolve("release/assets")
        Files.createDirectories(output)
        Files.writeString(output.resolve("stale.txt"), "stale")
        val stage = project.tasks.register("stage", LibfdxDesktopCppAssetsTask::class.java).get()
        stage.assets.from(root.resolve("assets"))
        stage.applicationClasspath.from(dependency)
        stage.outputDir.fileValue(output.toFile())

        stage.stage()

        assertFalse(Files.exists(output.resolve("stale.txt")))
        assertEquals("application", Files.readString(output.resolve("libfdx-assets/font.txt")))
        assertEquals("shared", Files.readString(output.resolve("libfdx-assets/license.txt")))
    }
}
