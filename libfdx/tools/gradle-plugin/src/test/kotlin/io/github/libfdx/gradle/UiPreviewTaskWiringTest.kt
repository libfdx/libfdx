package io.github.libfdx.gradle

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class UiPreviewTaskWiringTest {
    private fun project(): ProjectInternal {
        val root = Path.of("build/tmp/ui-preview-wiring").toAbsolutePath()
        Files.createDirectories(root)
        return ProjectBuilder.builder().withProjectDir(Files.createTempDirectory(root, "project-").toFile())
            .build() as ProjectInternal
    }

    @Test
    fun `preview is opt in and uses only the existing main source set`() {
        val project = project()
        project.pluginManager.apply(LibfdxGradlePlugin::class.java)
        val originalSources = project.extensions.getByType(SourceSetContainer::class.java).names.toSet()
        assertFalse(project.tasks.names.contains(UI_PREVIEW_RUN))
        val extension = project.extensions.getByType(LibfdxExtension::class.java)
        extension.uiPreview { mainClass.set("example.MenuPreview") }
        project.evaluate()
        assertTrue(project.tasks.names.containsAll(listOf(UI_PREVIEW_PREPARE, UI_PREVIEW_RUN, UI_PREVIEW_STOP)))
        assertEquals(originalSources, project.extensions.getByType(SourceSetContainer::class.java).names)
        val tool = project.configurations.getByName("libfdxUiPreviewToolClasspath")
        assertTrue(project.configurations.getByName("compileOnly").extendsFrom.contains(tool))
        assertFalse(project.configurations.getByName("runtimeClasspath").extendsFrom.contains(tool))
        val run = project.tasks.getByName(UI_PREVIEW_RUN)
        assertTrue(run.taskDependencies.getDependencies(run).contains(project.tasks.getByName(UI_PREVIEW_PREPARE)))
        assertFalse(project.tasks.names.any { it.contains("preview", true) && it.contains("generate", true) })
    }

    @Test
    fun `prepare writes direct launch metadata and watches dependency sources and project assets`() {
        val project = project()
        project.pluginManager.apply(JavaPlugin::class.java)
        val core = ProjectBuilder.builder().withName("core").withParent(project).build()
        core.pluginManager.apply(JavaPlugin::class.java)
        project.dependencies.add("implementation", project.dependencies.project(mapOf("path" to ":core")))
        project.file("gradle/wrapper").mkdirs()
        project.file("gradle/wrapper/gradle-wrapper.jar").writeBytes(byteArrayOf())
        val tool = project.configurations.create("previewTool")
        val sdk = project.file("tools_ui_preview.jar")
        sdk.writeBytes(byteArrayOf())
        project.dependencies.add(tool.name, project.files(sdk))
        val extension = project.objects.newInstance(LibfdxExtension::class.java, project)
        extension.assets.from(project.file("assets"))
        extension.uiPreview {
            javaLanguageVersion.set(Runtime.version().feature())
            watch.from(project.file("fixtures"))
            arguments.set(listOf("one argument with spaces"))
        }
        registerUiPreviewTasks(project, extension, tool)
        val prepare = project.tasks.getByName(UI_PREVIEW_PREPARE)
        prepare.actions.forEach { it.execute(prepare) }
        val model = Properties()
        project.file("build/ui-preview/launch.properties").reader().use { model.load(it) }
        assertEquals("", model.getProperty("mainClass"))
        assertEquals(sdk.absolutePath, model.getProperty("controllerClasspath"))
        assertEquals(":$UI_PREVIEW_PREPARE", model.getProperty("prepareTask"))
        assertEquals("one argument with spaces", model.getProperty("arguments.0"))
        assertEquals(project.file("assets").absolutePath, model.getProperty("assets.0"))
        val watches = (0 until model.getProperty("watch.count").toInt()).map { model.getProperty("watch.$it") }
        assertTrue(watches.contains(core.file("src/main/java").absolutePath))
        assertTrue(watches.contains(project.file("src/main/java").absolutePath))
        assertTrue(watches.contains(project.file("fixtures").absolutePath))
        assertTrue(watches.none { it.contains("build/generated") })
        assertEquals(setOf("main", "test"), project.extensions.getByType(SourceSetContainer::class.java).names)
    }
}
