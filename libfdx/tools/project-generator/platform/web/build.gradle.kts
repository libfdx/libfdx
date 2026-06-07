import io.github.libfdx.build.LibExt
import org.gradle.api.GradleException
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    id("java")
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("project_generator_web")
}

group = "${LibExt.fdxGroup}.tools.projectgenerator"

dependencies {
    implementation(project(":libfdx:tools:project-generator:core"))
    implementation(project(":libfdx:tools:project-generator:ui"))
    implementation(project(":libfdx:backends:web"))
    implementation(project(":libfdx:extensions:graphics:gl:platform:web"))
    implementation(libs.teavm.jso)
}

libfdx {
    js {
        mainClass.set("io.github.libfdx.tools.project.generator.web.ProjectGeneratorWebJsLauncher")
        htmlTitle.set("libfdx Project Generator - WebGL JS")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)
    }
    wasm {
        mainClass.set("io.github.libfdx.tools.project.generator.web.ProjectGeneratorWebWasmLauncher")
        htmlTitle.set("libfdx Project Generator - WebGL Wasm")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)
    }
}

val jsWebappDir = layout.buildDirectory.dir("dist/web-js/webapp")
val wasmWebappDir = layout.buildDirectory.dir("dist/web-wasm/webapp")
val ghPagesBranch = "gh-pages"
val ghPagesWorktreeDir = rootProject.layout.buildDirectory.dir("gh-pages-worktree")

tasks.register("build_web_js") {
    group = "application"
    description = "Builds the libfdx project generator WebGL JavaScript web application."
    dependsOn("libfdx_web_js_build")
}

tasks.register("build_web_wasm") {
    group = "application"
    description = "Builds the libfdx project generator WebGL Wasm web application."
    dependsOn("libfdx_web_wasm_build")
}

tasks.register<io.github.libfdx.gradle.LibfdxRunWebTask>("run_web_js") {
    group = "application"
    description = "Builds and serves the libfdx project generator WebGL JavaScript web application."
    dependsOn("build_web_js")
    webappDir.set(jsWebappDir)
    port.set(libfdx.js.serverPort)
    defaultPath.set("/")
}

tasks.register<io.github.libfdx.gradle.LibfdxRunWebTask>("run_web_wasm") {
    group = "application"
    description = "Builds and serves the libfdx project generator WebGL Wasm web application."
    dependsOn("build_web_wasm")
    webappDir.set(wasmWebappDir)
    port.set(libfdx.wasm.serverPort)
    defaultPath.set("/")
}

tasks.register("deploy_gh_pages") {
    group = "publishing"
    description = "Builds the project generator web app and commits it to project-generator/ on gh-pages."
    dependsOn("build_web_js")
    inputs.dir(jsWebappDir)
    outputs.upToDateWhen { false }
    doLast {
        val source = jsWebappDir.get().asFile
        if (!source.isDirectory) {
            throw GradleException("Project generator webapp was not built: ${source.absolutePath}")
        }

        val worktree = ghPagesWorktreeDir.get().asFile
        prepareGhPagesWorktree(worktree)
        val target = File(worktree, "project-generator")
        deleteDirectory(target)
        copyDirectoryContents(source, target)
        File(worktree, ".nojekyll").writeText("", Charsets.UTF_8)

        git(worktree, "add", ".nojekyll", "project-generator")
        val status = gitOutput(worktree, "status", "--porcelain")
        if (status.isBlank()) {
            logger.lifecycle("No gh-pages changes to commit.")
            return@doLast
        }
        git(worktree, "commit", "-m", "Deploy project generator")
        logger.lifecycle("Committed project generator webapp to $ghPagesBranch: ${worktree.absolutePath}")
    }
}

tasks.register<JavaExec>("test_archive_project") {
    group = "verification"
    description = "Runs the web project archive smoke checks."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.libfdx.tools.project.generator.web.WebProjectArchiveSmokeTest")
}

tasks.named<Test>("test") {
    enabled = false
}

tasks.named("check") {
    dependsOn("test_archive_project")
}

fun prepareGhPagesWorktree(worktree: File) {
    if (worktree.exists()) {
        git(rootProject.projectDir, "worktree", "remove", "--force", worktree.absolutePath, allowFailure = true)
        deleteDirectory(worktree)
    }
    git(rootProject.projectDir, "worktree", "prune", allowFailure = true)
    worktree.parentFile.mkdirs()

    when {
        hasRef("refs/heads/$ghPagesBranch") -> {
            git(rootProject.projectDir, "worktree", "add", worktree.absolutePath, ghPagesBranch)
        }
        hasRef("refs/remotes/origin/$ghPagesBranch") -> {
            git(rootProject.projectDir, "worktree", "add", "-b", ghPagesBranch,
                worktree.absolutePath, "origin/$ghPagesBranch")
        }
        else -> {
            git(rootProject.projectDir, "worktree", "add", "--detach", worktree.absolutePath, "HEAD")
            git(worktree, "checkout", "--orphan", ghPagesBranch)
            git(worktree, "rm", "-r", "-f", "--ignore-unmatch", ".")
        }
    }
}

fun hasRef(ref: String): Boolean {
    return git(rootProject.projectDir, "show-ref", "--verify", "--quiet", ref, allowFailure = true) == 0
}

fun gitOutput(workingDirectory: File, vararg args: String): String {
    return runCommand(workingDirectory, listOf("git") + args.toList()).first.trim()
}

fun git(workingDirectory: File, vararg args: String, allowFailure: Boolean = false): Int {
    return runCommand(workingDirectory, listOf("git") + args.toList(), allowFailure).second
}

fun runCommand(workingDirectory: File, command: List<String>, allowFailure: Boolean = false): Pair<String, Int> {
    val process = ProcessBuilder(command)
        .directory(workingDirectory)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()
    if (exitCode != 0 && !allowFailure) {
        throw GradleException("Command failed with exit code $exitCode: ${command.joinToString(" ")}\n$output")
    }
    return output to exitCode
}

fun deleteDirectory(path: File) {
    if (!path.exists()) {
        return
    }
    path.walkBottomUp().forEach { file ->
        if (!file.delete() && file.exists()) {
            throw GradleException("Could not delete ${file.absolutePath}")
        }
    }
}

fun copyDirectoryContents(sourceRoot: File, outputRoot: File) {
    val normalizedSourceRoot = sourceRoot.canonicalFile.toPath()
    val normalizedOutputRoot = outputRoot.canonicalFile.toPath()
    sourceRoot.walkTopDown()
        .filter { it.isFile }
        .forEach { source ->
            val relative = normalizedSourceRoot.relativize(source.canonicalFile.toPath())
            val output = normalizedOutputRoot.resolve(relative).normalize()
            if (!output.startsWith(normalizedOutputRoot)) {
                throw GradleException("Refusing to copy outside output directory: ${source.absolutePath}")
            }
            Files.createDirectories(output.parent)
            Files.copy(source.toPath(), output, StandardCopyOption.REPLACE_EXISTING)
        }
}
