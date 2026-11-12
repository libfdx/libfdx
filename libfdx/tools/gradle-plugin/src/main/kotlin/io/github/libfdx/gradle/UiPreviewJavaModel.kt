package io.github.libfdx.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import java.io.File
import java.nio.file.Path
import java.util.Properties
import java.util.jar.JarFile

/** Captures the existing compiler contract once; quick edits never run Gradle configuration. */
internal fun previewJavaModel(project: Project, model: Properties, shared: Set<File>, runtime: List<File> = emptyList()): Set<Path> {
    val projects = linkedSetOf<Project>()
    fun visit(current: Project) {
        if (current in projects) return
        projects.add(current)
        current.configurations.findByName("runtimeClasspath")?.allDependencies
            ?.withType(ProjectDependency::class.java)?.forEach {
                current.rootProject.findProject(it.path)?.let { dependency -> visit(dependency) }
            }
    }
    visit(project)
    val modules = projects.mapNotNull { current ->
        val sources = current.extensions.findByType(SourceSetContainer::class.java)?.findByName("main")
            ?: return@mapNotNull null
        val compile = current.tasks.findByName(sources.compileJavaTaskName) as? JavaCompile
            ?: return@mapNotNull null
        Triple(current, sources, compile)
    }
    model.setProperty("java.count", modules.size.toString())
    modules.forEachIndexed { index, (current, _, compile) ->
        val key = "java.$index"
        val build = current.layout.buildDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        val args = compile.options.compilerArgs
        val ordinaryArgs = args.all {
            it in setOf("-parameters", "-nowarn", "-deprecation", "-Werror") ||
                it.startsWith("-Xlint") || it.startsWith("-g") || it.startsWith("-Xdiags:")
        }
        // Annotation processors and compiler plugins can generate/validate additional outputs.
        // Their edits continue through the real Gradle task instead of bypassing that contract.
        val direct = ordinaryArgs && compile.options.annotationProcessorPath?.files.orEmpty().isEmpty() &&
            compile.options.compilerArgumentProviders.isEmpty() &&
            compile.source.files.none { it.name == "module-info.java" } &&
            runtime.none { file ->
                file.isFile && file.extension == "jar" && file.toPath().toAbsolutePath().normalize().startsWith(build) &&
                    JarFile(file, false).use { jar -> jar.entries().asSequence().any {
                        it.name.startsWith("META-INF/versions/") && it.name.endsWith(".class")
                    } }
            }
        model.setProperty("$key.direct", direct.toString())
        model.setProperty("$key.output", compile.destinationDirectory.get().asFile.absolutePath)
        model.setProperty("$key.reloadable", shared.none {
            it.toPath().toAbsolutePath().normalize().startsWith(build)
        }.toString())
        previewValues(model, "$key.sources", compile.source.files.filter {
            it.extension == "java" && !it.toPath().toAbsolutePath().normalize().startsWith(build)
        }.map(File::getAbsolutePath))
        previewValues(model, "$key.classpath", compile.classpath.files.map(File::getAbsolutePath))
        val options = mutableListOf("-encoding", compile.options.encoding ?: "UTF-8")
        val release = compile.options.release.orNull
        if (release != null) options.addAll(listOf("--release", release.toString()))
        else options.addAll(listOf("-source", compile.sourceCompatibility, "-target", compile.targetCompatibility))
        options.addAll(args)
        previewValues(model, "$key.options", options)
    }
    return modules.map { it.first.layout.buildDirectory.get().asFile.toPath().toAbsolutePath().normalize() }.toSet()
}
