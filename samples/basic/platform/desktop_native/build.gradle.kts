plugins {
    id("java")
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "io.github.libfdx.samples.basic"

val nativeTargetFileName = "libfdx-basic-gl-desktop-native"
val nativeOpenConsole = providers.gradleProperty("libfdx.desktopNative.openConsole")
        .map { value -> value.toBooleanStrictOrNull() ?: value.toBoolean() }
        .orElse(true)

base {
    archivesName.set("sample_basic_desktop_native")
}

dependencies {
    implementation(project(":samples:basic:core"))
    implementation(project(":libfdx:backends:desktop_native"))

    runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop_native"))
}

libfdx {
    desktopNative {
        mainClass.set("io.github.libfdx.samples.basic.desktopnative.BasicDesktopNativeLauncher")
        targetFileName.set(nativeTargetFileName)
        buildType.set("Debug")
    }
}

fun isWindowsHost(): Boolean {
    return System.getProperty("os.name", "").lowercase().contains("win")
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

registerDesktopNativeSampleTask(
        "run_gl_debug",
        "Runs the basic desktop_native GL sample using the Debug native executable.",
        "libfdx_desktop_native_build_debug",
        "Debug")

registerDesktopNativeSampleTask(
        "run_gl_release",
        "Runs the basic desktop_native GL sample using the Release native executable.",
        "libfdx_desktop_native_build_release",
        "Release")
