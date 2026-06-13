import io.github.libfdx.build.LibExt

import org.gradle.api.file.FileCollection
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.file.Path

plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.samples.basic"


val nativeTargetFileName = "libfdx-basic-gl-desktop-native"
val nativeBuildRoot = layout.buildDirectory.dir("dist/desktop-native")
val nativeShowConsole = providers.gradleProperty("libfdx.desktopNative.showConsole")
        .map { value -> value.toBooleanStrictOrNull() ?: value.toBoolean() }
        .orElse(true)
val nativeOpenConsole = providers.gradleProperty("libfdx.desktopNative.openConsole")
        .map { value -> value.toBooleanStrictOrNull() ?: value.toBoolean() }
        .orElse(true)

base {
    archivesName.set("sample_basic_desktop_native")
}

dependencies {
    implementation(project(":samples:basic:core"))
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_desktop_native:${LibExt.publishedLibfdxVersion}")

        runtimeOnly("${LibExt.fdxGroup}:gl_desktop_native:${LibExt.publishedLibfdxVersion}")
    } else {
        implementation(project(":libfdx:backends:desktop_native"))

        runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop_native"))
    }
}

val builderClasspath = sourceSets["main"].runtimeClasspath

fun isWindowsHost(): Boolean {
    return System.getProperty("os.name", "").lowercase().contains("win")
}

fun nativeBuildScript(scriptBaseName: String): File {
    return nativeBuildRoot.get().asFile.resolve(scriptBaseName + if(isWindowsHost()) ".bat" else ".sh")
}

fun registerNativeGenerateTask(taskName: String, nativeBuildType: String) {
    tasks.register(taskName) {
        group = "application"
        description = "Generates the basic desktop_native GL sample $nativeBuildType project."
        dependsOn(builderClasspath)
        inputs.files(builderClasspath)
        outputs.dir(nativeBuildRoot)
        doLast {
            runNativeBuilder(
                    builderClasspath,
                    "io.github.libfdx.samples.basic.desktopnative.BasicDesktopNativeLauncher",
                    nativeBuildRoot.get().asFile,
                    nativeTargetFileName,
                    nativeBuildType,
                    nativeShowConsole.get())
        }
    }
}

fun registerNativeBuildTask(taskName: String, descriptionText: String, generateTask: String, scriptBaseName: String) {
    tasks.register<Exec>(taskName) {
        group = "application"
        description = descriptionText
        dependsOn(generateTask)
        doFirst {
            val script = nativeBuildScript(scriptBaseName)
            if(!script.isFile) {
                throw GradleException("Native build script was not generated: ${script.absolutePath}")
            }
            workingDir = nativeBuildRoot.get().asFile
            commandLine(if(isWindowsHost()) listOf("cmd", "/c", script.absolutePath)
                    else listOf("bash", script.absolutePath))
        }
    }
}

fun windowsPowerShellStartCommand(executable: File, args: List<String>, workingDirectory: File): List<String> {
    val argumentList = args.joinToString(", ") { powershellSingleQuoted(it) }
    val script = "${'$'}p = Start-Process -FilePath ${powershellSingleQuoted(executable.absolutePath)} " +
            "-ArgumentList @($argumentList) " +
            "-WorkingDirectory ${powershellSingleQuoted(workingDirectory.absolutePath)} " +
            "-Wait -PassThru; exit ${'$'}p.ExitCode"
    return listOf("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script)
}

fun powershellSingleQuoted(value: String): String {
    return "'" + value.replace("'", "''") + "'"
}

fun registerDesktopNativeSampleTask(taskName: String, descriptionText: String, nativeBuildTask: String,
        nativeBuildType: String) {
    tasks.register<Exec>(taskName) {
        group = "application"
        description = descriptionText
        dependsOn(nativeBuildTask)
        workingDir = rootProject.projectDir
        doFirst {
            val suffix = if(nativeBuildType.equals("release", ignoreCase = true)) "release" else "debug"
            val executable = layout.buildDirectory.file(
                    "dist/desktop-native/c/release/$nativeTargetFileName" + "_$suffix"
                            + if(isWindowsHost()) ".exe" else "").get().asFile
            if(!executable.isFile) {
                throw GradleException("Native executable was not built: ${executable.absolutePath}")
            }
            val args = mutableListOf<String>()
            val exitAfterFrames = System.getProperty("libfdx.sample.exitAfterFrames")
            if(!exitAfterFrames.isNullOrBlank()) {
                args.add(exitAfterFrames)
            }
            if(isWindowsHost() && nativeOpenConsole.get()) {
                commandLine(windowsPowerShellStartCommand(executable, args, rootProject.projectDir))
            }
            else {
                commandLine(listOf(executable.absolutePath) + args)
            }
        }
    }
}

fun runNativeBuilder(classpath: FileCollection, mainClassName: String, buildRoot: File, targetFileName: String,
        nativeBuildType: String, showConsole: Boolean) {
    withBuilderClassLoader(classpath) { classLoader ->
        val builderClass = classLoader.loadClass("io.github.libfdx.backend.desktopnative.NativeBuilder")
        var builder = builderClass.getMethod("desktop").invoke(null)
        val paths = classpath.files.map { it.toPath() }
        builder = invokeBuilder(builder, "classpath", listOf(Collection::class.java), listOf(paths))
        builder = invokeBuilder(builder, "nativeResourceClasspath", listOf(Collection::class.java), listOf(paths))
        builder = invokeBuilder(builder, "buildRoot", listOf(Path::class.java), listOf(buildRoot.toPath()))
        builder = invokeBuilder(builder, "mainClass", listOf(String::class.java), listOf(mainClassName))
        builder = invokeBuilder(builder, "targetFileName", listOf(String::class.java), listOf(targetFileName))
        builder = invokeBuilder(builder, "buildType", listOf(String::class.java), listOf(nativeBuildType))
        builder = invokeBuilder(builder, "showConsole", listOf(Boolean::class.javaPrimitiveType!!), listOf(showConsole))
        invokeBuilder(builder, "build", emptyList(), emptyList())
    }
}

fun withBuilderClassLoader(classpath: FileCollection, action: (ClassLoader) -> Unit) {
    val urls = classpath.files.map { it.toURI().toURL() }.toTypedArray()
    URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { classLoader ->
        val previous = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = classLoader
        try {
            action(classLoader)
        }
        finally {
            Thread.currentThread().contextClassLoader = previous
        }
    }
}

fun invokeBuilder(target: Any, methodName: String, parameterTypes: List<Class<*>>, args: List<Any>): Any {
    try {
        return target.javaClass.getMethod(methodName, *parameterTypes.toTypedArray())
                .invoke(target, *args.toTypedArray()) ?: target
    }
    catch (error: InvocationTargetException) {
        val cause = error.targetException
        if (cause is RuntimeException) {
            throw cause
        }
        if (cause is Error) {
            throw cause
        }
        throw GradleException("Builder method '$methodName' failed.", cause)
    }
}

registerNativeGenerateTask("basic_desktop_native_gl_debug_generate", "Debug")
registerNativeGenerateTask("basic_desktop_native_gl_release_generate", "Release")

registerNativeBuildTask("basic_desktop_native_gl_debug_build",
        "Builds the basic desktop_native GL sample Debug executable.",
        "basic_desktop_native_gl_debug_generate",
        "app_debug")

registerNativeBuildTask("basic_desktop_native_gl_release_build",
        "Builds the basic desktop_native GL sample Release executable.",
        "basic_desktop_native_gl_release_generate",
        "app_release")

registerDesktopNativeSampleTask(
        "basic_desktop_native_gl_debug_run",
        "Runs the basic desktop_native GL sample using the Debug native executable.",
        "basic_desktop_native_gl_debug_build",
        "Debug")

registerDesktopNativeSampleTask(
        "basic_desktop_native_gl_release_run",
        "Runs the basic desktop_native GL sample using the Release native executable.",
        "basic_desktop_native_gl_release_build",
        "Release")
