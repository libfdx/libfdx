package io.github.libfdx.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

// jNative owns these build/output directories; they are not generation inputs or outputs.
internal fun desktopCppProjectFiles(directory: DirectoryProperty): FileTree = directory.asFileTree.matching {
    exclude("b/**", "debug/**", "release/**", "diagnostics/**", "configure-*.log", "compile-*.log")
}

@DisableCachingByDefault(because = "Copies local application and dependency assets")
abstract class LibfdxDesktopCppAssetsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assets: ConfigurableFileCollection

    @get:Classpath
    abstract val applicationClasspath: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun stage() {
        val output = outputDir.get().asFile
        copyAssetRoots(assets.files, output)
        copySharedAssetResources(applicationClasspath.files, output)
    }
}

@DisableCachingByDefault(because = "jNative projects contain absolute host paths")
abstract class LibfdxDesktopCppGenerateTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @get:Internal
    abstract val buildRoot: DirectoryProperty

    @get:Internal
    abstract val nativeProjectDir: DirectoryProperty

    @get:OutputFiles
    val generatedFiles: FileTree
        get() = desktopCppProjectFiles(nativeProjectDir)

    @get:Input
    abstract val mainClass: Property<String>

    @get:Input
    abstract val targetFileName: Property<String>

    @get:Input
    abstract val buildType: Property<String>

    @get:Input
    abstract val debugInformation: Property<Boolean>

    @get:Input
    abstract val sourceLayout: Property<String>

    @get:Classpath
    abstract val applicationClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val toolClasspath: ConfigurableFileCollection

    init {
        nativeProjectDir.convention(buildRoot.dir("native"))
    }

    @TaskAction
    fun generate() {
        val request = LibfdxToolRequest().apply {
            value("action", "generate")
            value("buildRoot", buildRoot.get().asFile.toPath())
            value("mainClass", mainClass.get())
            value("targetFileName", targetFileName.get())
            value("buildType", buildType.get())
            value("debugInformation", debugInformation.get())
            value("sourceLayout", sourceLayout.get())
            paths("applicationClasspath", applicationClasspath.files.filter(File::exists).map(File::toPath))
        }.write(File(temporaryDir, "desktop-cpp-generate.properties"))
        // The backend discovers native provider manifests through its class loader.
        executeLibfdxTool(execOperations, toolClasspath + applicationClasspath, DESKTOP_CPP_PROJECT_TOOL_CLASS, request)
    }
}

@DisableCachingByDefault(because = "Native compilation depends on the installed host toolchain")
abstract class LibfdxDesktopCppBuildTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @get:Internal
    abstract val projectDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val projectFiles: FileTree
        get() = desktopCppProjectFiles(projectDir)

    @get:OutputDirectory
    abstract val releaseDir: DirectoryProperty

    @get:Input
    abstract val buildType: Property<String>

    @get:Input
    abstract val cmakeExecutable: Property<String>

    @get:Input
    abstract val generator: Property<String>

    @get:Input
    abstract val cmakeArguments: ListProperty<String>

    @get:Input
    abstract val cmakeBuildArguments: ListProperty<String>

    @get:Input
    abstract val buildTimeoutMinutes: Property<Int>

    @get:Classpath
    abstract val toolClasspath: ConfigurableFileCollection

    init {
        releaseDir.convention(projectDir.zip(buildType) { directory, variant -> directory.dir(variant) })
        // Always let CMake check the actual compiler and native dependencies.
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun build() {
        val request = LibfdxToolRequest().apply {
            value("action", "build")
            value("projectDirectory", projectDir.get().asFile.toPath())
            value("buildType", buildType.get())
            value("cmakeExecutable", cmakeExecutable.get())
            value("generator", generator.get())
            value("buildTimeoutMinutes", buildTimeoutMinutes.get())
            value("cmakeArguments.count", cmakeArguments.get().size)
            cmakeArguments.get().forEachIndexed { index, argument -> value("cmakeArguments.$index", argument) }
            value("cmakeBuildArguments.count", cmakeBuildArguments.get().size)
            cmakeBuildArguments.get().forEachIndexed { index, argument -> value("cmakeBuildArguments.$index", argument) }
        }.write(File(temporaryDir, "desktop-cpp-build.properties"))
        executeLibfdxTool(execOperations, toolClasspath, DESKTOP_CPP_PROJECT_TOOL_CLASS, request)
    }
}

@DisableCachingByDefault(because = "Launches a desktop executable")
abstract class LibfdxDesktopCppRunTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val releaseDir: DirectoryProperty

    @get:Input
    abstract val targetFileName: Property<String>

    @get:Input
    abstract val runArgs: ListProperty<String>

    @TaskAction
    fun run() {
        val suffix = if(System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
        val executable = releaseDir.file(targetFileName.map { it + suffix }).get().asFile
        check(executable.isFile) { "Native executable was not built: ${executable.absolutePath}" }
        execOperations.exec {
            executable(executable.absolutePath)
            args(runArgs.get())
            workingDir(releaseDir.dir("assets").get().asFile)
            standardInput = System.`in`
        }.assertNormalExitValue()
    }
}
