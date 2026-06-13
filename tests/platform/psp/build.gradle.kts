import io.github.libfdx.build.LibExt

import org.gradle.api.file.FileCollection
import java.lang.reflect.InvocationTargetException
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.zip.ZipInputStream

plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.tests"


base {
    archivesName.set("tests_psp")
}

dependencies {
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_psp:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:g2d:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:ui_kit:${LibExt.publishedLibfdxVersion}")
    } else {
        implementation(project(":libfdx:backends:psp"))
        implementation(project(":libfdx:graphics:g2d"))
        implementation(project(":libfdx:ui:ui-kit"))
    }
}

val builderClasspath = sourceSets["main"].runtimeClasspath
val pspAssetsDir = rootProject.layout.projectDirectory.dir("tests/assets")
val pspFontFile = rootProject.layout.projectDirectory.file("tests/assets/font/freetype/lsans.ttf")
val pspToolDir = layout.buildDirectory.dir("tools/ppsspp")
val pspAutoDownload = providers.gradleProperty("libfdx.psp.ppssppAutoDownload")
        .map { value -> value.toBooleanStrictOrNull() ?: value.toBoolean() }
        .orElse(true)
val pspDownloadUrl = providers.gradleProperty("libfdx.psp.ppssppDownloadUrl")
        .orElse(providers.environmentVariable("PPSSPP_DOWNLOAD_URL"))
        .orElse("https://www.ppsspp.org/files/1_20_4/ppsspp_win.zip")
val pspExecutable = providers.gradleProperty("libfdx.psp.ppssppExecutable")
        .orElse(providers.environmentVariable("PPSSPP_EXECUTABLE"))
        .orElse("")
val pspCaptureDelaySeconds = providers.gradleProperty("libfdx.psp.ppssppCaptureDelaySeconds")
        .map(String::toInt)
        .orElse(6)

data class PspVariant(
        val id: String,
        val generateTask: String,
        val buildTask: String,
        val captureTask: String,
        val mainClassName: String,
        val targetFileName: String,
        val label: String
)

fun isWindowsHost(): Boolean {
    return System.getProperty("os.name", "").lowercase().contains("win")
}

fun pspBuildScript(buildRoot: Provider<Directory>): File {
    return buildRoot.get().asFile.resolve("build" + if(isWindowsHost()) ".bat" else ".sh")
}

fun registerPspVariant(variant: PspVariant) {
    val buildRoot = layout.buildDirectory.dir("dist/psp/${variant.id}")
    val releaseDir = buildRoot.map { it.dir("c/release") }
    val captureFile = layout.buildDirectory.file("reports/ppsspp/${variant.targetFileName}.png")
    tasks.register(variant.generateTask) {
        group = "application"
        description = "Generates the libfdx PSP ${variant.label} TeaVM C project."
        dependsOn(builderClasspath)
        inputs.files(builderClasspath)
        inputs.dir(pspAssetsDir)
        inputs.file(pspFontFile)
        outputs.dir(buildRoot)
        doLast {
            runPspBuilder(
                    builderClasspath,
                    variant.mainClassName,
                    buildRoot.get().asFile,
                    variant.targetFileName,
                    pspAssetsDir.asFile,
                    pspFontFile.asFile)
        }
    }
    tasks.register<Exec>(variant.buildTask) {
        group = "application"
        description = "Generates and builds the libfdx PSP ${variant.label} EBOOT project."
        dependsOn(variant.generateTask)
        doFirst {
            val script = pspBuildScript(buildRoot)
            if(!script.isFile) {
                throw GradleException("PSP build script was not generated: ${script.absolutePath}")
            }
            workingDir = buildRoot.get().asFile
            commandLine(if(isWindowsHost()) listOf("cmd", "/c", script.absolutePath)
                    else listOf("bash", script.absolutePath))
        }
    }
    tasks.register(variant.captureTask) {
        group = "application"
        description = "Builds the libfdx PSP ${variant.label} and captures a PPSSPP emulator frame."
        dependsOn(variant.buildTask)
        inputs.dir(releaseDir)
        outputs.file(captureFile)
        doLast {
            capturePspFrame(
                    releaseDir.get().asFile.toPath(),
                    variant.targetFileName,
                    captureFile.get().asFile.toPath(),
                    pspToolDir.get().asFile.toPath(),
                    pspCaptureDelaySeconds.get(),
                    pspAutoDownload.get(),
                    pspDownloadUrl.get(),
                    pspExecutable.get())
        }
    }
}

fun runPspBuilder(classpath: FileCollection, mainClassName: String, buildRoot: File, targetFileName: String,
        assetsDir: File, fontFile: File) {
    withBuilderClassLoader(classpath) { classLoader ->
        val builderClass = classLoader.loadClass("io.github.libfdx.backend.psp.PspBuilder")
        var builder = builderClass.getMethod("psp").invoke(null)
        val paths = classpath.files.map { it.toPath() }
        builder = invokeBuilder(builder, "classpath", listOf(Collection::class.java), listOf(paths))
        builder = invokeBuilder(builder, "nativeResourceClasspath", listOf(Collection::class.java), listOf(paths))
        builder = invokeBuilder(builder, "buildRoot", listOf(Path::class.java), listOf(buildRoot.toPath()))
        builder = invokeBuilder(builder, "mainClass", listOf(String::class.java), listOf(mainClassName))
        builder = invokeBuilder(builder, "targetFileName", listOf(String::class.java), listOf(targetFileName))
        val optimizationClass = classLoader.loadClass("io.github.libfdx.backend.teavm.shared.TeaVMOptimization")
        builder = invokeBuilder(builder, "optimization", listOf(optimizationClass), listOf(enumValue(optimizationClass, "BALANCED")))
        builder = invokeBuilder(builder, "debugInformation", listOf(Boolean::class.javaPrimitiveType!!), listOf(true))
        builder = invokeBuilder(builder, "debugMemory", listOf(Boolean::class.javaPrimitiveType!!), listOf(false))
        builder = invokeBuilder(builder, "maxHeapSize", listOf(Integer.TYPE), listOf(32))
        builder = invokeBuilder(builder, "assets", listOf(Collection::class.java), listOf(listOf(assetsDir.toPath())))
        builder = invokeBuilder(builder, "bitmapFont",
                listOf(Path::class.java, String::class.java, Integer.TYPE, String::class.java),
                listOf(fontFile.toPath(), "psp_test_bitmap", 24, "font/bitmap"))
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

fun enumValue(enumClass: Class<*>, value: String): Any {
    return Enum::class.java.getMethod("valueOf", Class::class.java, String::class.java)
            .invoke(null, enumClass, value)
}

fun capturePspFrame(releaseDir: Path, projectName: String, capture: Path, toolDir: Path, delaySeconds: Int,
        autoDownload: Boolean, downloadUrl: String, configuredExecutable: String) {
    val eboot = releaseDir.resolve("EBOOT.PBP")
    if (!Files.isRegularFile(eboot)) {
        throw GradleException("PSP EBOOT was not built: $eboot")
    }
    val executable = resolvePpsspp(toolDir, autoDownload, downloadUrl, configuredExecutable)
    val launchDir = preparePpssppLaunchDirectory(executable, projectName)
    copyTree(releaseDir, launchDir)
    Files.createDirectories(capture.parent)
    Files.deleteIfExists(capture)
    val tmpDir = toolDir.resolve("capture").resolve(projectName)
    Files.createDirectories(tmpDir)
    val config = writePpssppCaptureConfig(tmpDir.resolve("ppsspp-capture.ini"))
    val argsFile = tmpDir.resolve("ppsspp-args.txt")
    val screenshotDirsFile = tmpDir.resolve("ppsspp-screenshot-dirs.txt")
    val script = writePpssppCaptureScript(tmpDir.resolve("capture-ppsspp.ps1"))
    val launchArgs = listOf(
            "--windowed",
            "--escape-exit",
            "--appendconfig=${config.toAbsolutePath().normalize()}")
    Files.writeString(argsFile, launchArgs.joinToString(System.lineSeparator()))
    Files.writeString(screenshotDirsFile, ppssppScreenshotDirectories(executable)
            .joinToString(System.lineSeparator()) { it.toString() })
    val process = ProcessBuilder(listOf(
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            script.toString(),
            "-Ppsspp",
            executable.toString(),
            "-Eboot",
            launchDir.resolve("EBOOT.PBP").toString(),
            "-Capture",
            capture.toString(),
            "-DelaySeconds",
            maxOf(1, delaySeconds).toString(),
            "-ArgsFile",
            argsFile.toString(),
            "-ScreenshotDirsFile",
            screenshotDirsFile.toString()))
            .directory(launchDir.toFile())
            .redirectErrorStream(true)
            .start()
    process.inputStream.bufferedReader().useLines { lines ->
        lines.forEach { logger.lifecycle(it) }
    }
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw GradleException("PPSSPP capture failed with exit code $exitCode")
    }
    if (!Files.isRegularFile(capture) || Files.size(capture) == 0L) {
        throw GradleException("PPSSPP capture was not written: $capture")
    }
    logger.lifecycle("Captured $projectName PPSSPP frame: $capture")
}

fun resolvePpsspp(toolDir: Path, autoDownload: Boolean, downloadUrl: String, configuredExecutable: String): Path {
    val candidates = linkedSetOf<Path>()
    addPpssppCandidate(candidates, configuredExecutable)
    addPpssppCandidate(candidates, System.getenv("PPSSPP_EXECUTABLE"))
    System.getenv("PPSSPP_HOME")?.takeIf { it.isNotBlank() }?.let { home ->
        candidates.add(Path.of(home, "PPSSPPWindows64.exe"))
        candidates.add(Path.of(home, "PPSSPP.exe"))
    }
    System.getenv("PATH").orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }.forEach { pathEntry ->
        candidates.add(Path.of(pathEntry, "PPSSPPWindows64.exe"))
        candidates.add(Path.of(pathEntry, "PPSSPP.exe"))
        candidates.add(Path.of(pathEntry, "ppsspp.exe"))
    }
    System.getenv("ProgramFiles")?.let { programFiles ->
        candidates.add(Path.of(programFiles, "PPSSPP", "PPSSPPWindows64.exe"))
        candidates.add(Path.of(programFiles, "PPSSPP", "PPSSPP.exe"))
    }
    findPpsspp(toolDir)?.let(candidates::add)
    candidates.firstOrNull { Files.isRegularFile(it) }?.let { return it.toAbsolutePath().normalize() }
    if (autoDownload) {
        return downloadPpsspp(toolDir, downloadUrl)
    }
    throw GradleException("PPSSPP executable was not found.")
}

fun addPpssppCandidate(candidates: MutableSet<Path>, value: String?) {
    if (!value.isNullOrBlank()) {
        candidates.add(Path.of(value))
    }
}

fun findPpsspp(root: Path): Path? {
    if (!Files.isDirectory(root)) {
        return null
    }
    Files.walk(root).use { stream ->
        return stream
                .filter(Files::isRegularFile)
                .filter { path ->
                    val name = path.fileName.toString().lowercase(Locale.ROOT)
                    name == "ppssppwindows64.exe" || name == "ppsspp.exe"
                }
                .findFirst()
                .orElse(null)
    }
}

fun downloadPpsspp(toolDir: Path, url: String): Path {
    Files.createDirectories(toolDir)
    val zip = toolDir.resolve("ppsspp.zip")
    val output = toolDir.resolve("portable")
    logger.lifecycle("Downloading portable PPSSPP from $url")
    URI.create(url).toURL().openStream().use { input ->
        Files.copy(input, zip, StandardCopyOption.REPLACE_EXISTING)
    }
    deleteTree(output)
    unzip(zip, output)
    return findPpsspp(output)
            ?: throw GradleException("Downloaded PPSSPP archive did not contain a Windows executable: $zip")
}

fun preparePpssppLaunchDirectory(executable: Path, projectName: String): Path {
    val roots = listOf(
            executable.parent.resolve("memstick"),
            Path.of(System.getProperty("user.home"), "Documents", "PPSSPP"),
            Path.of(System.getProperty("java.io.tmpdir"), "libfdx-ppsspp"))
    for (root in roots) {
        val gameDir = root.resolve(Path.of("PSP", "GAME", projectName))
        deleteTree(gameDir)
        Files.createDirectories(gameDir)
        if (Files.isDirectory(gameDir)) {
            return gameDir
        }
    }
    throw GradleException("Could not prepare a PPSSPP game directory.")
}

fun ppssppScreenshotDirectories(executable: Path): List<Path> {
    val userHome = Path.of(System.getProperty("user.home"))
    return listOf(
            executable.parent.resolve("memstick").resolve("PSP").resolve("SCREENSHOT"),
            userHome.resolve("Documents").resolve("PPSSPP").resolve("PSP").resolve("SCREENSHOT"),
            userHome.resolve("OneDrive").resolve("Documents").resolve("PPSSPP").resolve("PSP").resolve("SCREENSHOT"),
            userHome.resolve("PPSSPP").resolve("PSP").resolve("SCREENSHOT"))
            .map { it.toAbsolutePath().normalize() }
            .distinct()
}

fun writePpssppCaptureConfig(config: Path): Path {
    Files.writeString(config, """
        [General]
        FirstRun = False
        CheckForNewVersion = False
        UISound = False
        AutoRun = True
        ShowMenuBar = True
        WindowWidth = 960
        WindowHeight = 544
        WindowSizeState = 0
        TransparentBackground = False
        ScreenshotsAsPNG = True

        [Graphics]
        iShowStatusFlags = 0

        [Control]
        ShowTouchControls = False
        """.trimIndent())
    return config
}

fun writePpssppCaptureScript(script: Path): Path {
    Files.writeString(script, """
        param(
            [Parameter(Mandatory=${'$'}true)][string]${'$'}Ppsspp,
            [Parameter(Mandatory=${'$'}true)][string]${'$'}Eboot,
            [Parameter(Mandatory=${'$'}true)][string]${'$'}Capture,
            [Parameter(Mandatory=${'$'}true)][int]${'$'}DelaySeconds,
            [Parameter(Mandatory=${'$'}true)][string]${'$'}ArgsFile,
            [Parameter(Mandatory=${'$'}true)][string]${'$'}ScreenshotDirsFile
        )

        ${'$'}ErrorActionPreference = 'Stop'
        Add-Type @"
        using System;
        using System.Runtime.InteropServices;
        using System.Text;

        public static class LibfdxPpssppCapture {
            private const uint MF_BYPOSITION = 0x400;
            private const uint WM_COMMAND = 0x0111;
            private const uint INVALID_MENU_ID = 0xFFFFFFFF;

            [DllImport("user32.dll")]
            public static extern bool SetForegroundWindow(IntPtr hWnd);

            [DllImport("user32.dll")]
            public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

            [DllImport("user32.dll")]
            public static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);

            [DllImport("user32.dll")]
            public static extern IntPtr GetMenu(IntPtr hWnd);

            [DllImport("user32.dll")]
            public static extern int GetMenuItemCount(IntPtr hMenu);

            [DllImport("user32.dll")]
            public static extern IntPtr GetSubMenu(IntPtr hMenu, int nPos);

            [DllImport("user32.dll")]
            public static extern uint GetMenuItemID(IntPtr hMenu, int nPos);

            [DllImport("user32.dll", CharSet = CharSet.Unicode)]
            public static extern int GetMenuString(IntPtr hMenu, int uIDItem, StringBuilder lpString, int nMaxCount, uint uFlag);

            [DllImport("user32.dll")]
            public static extern IntPtr SendMessage(IntPtr hWnd, uint Msg, IntPtr wParam, IntPtr lParam);

            public static bool SendMenuCommandByText(IntPtr hWnd, string text) {
                IntPtr menu = GetMenu(hWnd);
                if (menu == IntPtr.Zero) {
                    return false;
                }
                return SendMenuCommandByText(hWnd, menu, text, 0);
            }

            private static bool SendMenuCommandByText(IntPtr hWnd, IntPtr menu, string text, int depth) {
                if (depth > 8) {
                    return false;
                }
                int count = GetMenuItemCount(menu);
                for (int i = 0; i < count; i++) {
                    StringBuilder label = new StringBuilder(256);
                    GetMenuString(menu, i, label, label.Capacity, MF_BYPOSITION);
                    string itemText = label.ToString().Replace("&", "");
                    uint id = GetMenuItemID(menu, i);
                    if (id != INVALID_MENU_ID && itemText.IndexOf(text, StringComparison.OrdinalIgnoreCase) >= 0) {
                        SendMessage(hWnd, WM_COMMAND, new IntPtr((long)id), IntPtr.Zero);
                        return true;
                    }
                    IntPtr submenu = GetSubMenu(menu, i);
                    if (submenu != IntPtr.Zero && SendMenuCommandByText(hWnd, submenu, text, depth + 1)) {
                        return true;
                    }
                }
                return false;
            }
        }
        "@

        function Quote-Argument([string]${'$'}Value) {
            return '"' + (${'$'}Value -replace '"', '\"') + '"'
        }

        if (!(Test-Path -LiteralPath ${'$'}Ppsspp)) {
            throw "PPSSPP executable does not exist: ${'$'}Ppsspp"
        }
        if (!(Test-Path -LiteralPath ${'$'}Eboot)) {
            throw "EBOOT does not exist: ${'$'}Eboot"
        }

        ${'$'}emulatorArgs = @()
        if (Test-Path -LiteralPath ${'$'}ArgsFile) {
            ${'$'}emulatorArgs = @(Get-Content -LiteralPath ${'$'}ArgsFile | Where-Object { ${'$'}_.Length -gt 0 })
        }
        ${'$'}screenshotDirs = @(Get-Content -LiteralPath ${'$'}ScreenshotDirsFile | Where-Object { ${'$'}_.Length -gt 0 })
        foreach (${'$'}dir in ${'$'}screenshotDirs) {
            New-Item -ItemType Directory -Force -Path ${'$'}dir | Out-Null
        }
        ${'$'}captureStartedAt = [DateTime]::UtcNow
        ${'$'}allArgs = @(${'$'}emulatorArgs + @(${'$'}Eboot))
        ${'$'}argumentList = (${'$'}allArgs | ForEach-Object { Quote-Argument ${'$'}_ }) -join ' '
        Write-Host "Launching PPSSPP: ${'$'}Ppsspp ${'$'}argumentList"

        ${'$'}process = Start-Process -FilePath ${'$'}Ppsspp -ArgumentList ${'$'}argumentList -WorkingDirectory (Split-Path -Parent ${'$'}Eboot) -PassThru
        try {
            ${'$'}deadline = (Get-Date).AddSeconds(30)
            ${'$'}handle = [IntPtr]::Zero
            while ((Get-Date) -lt ${'$'}deadline) {
                ${'$'}process.Refresh()
                if (${'$'}process.HasExited) {
                    throw "PPSSPP exited before creating a window."
                }
                if (${'$'}process.MainWindowHandle -ne [IntPtr]::Zero) {
                    ${'$'}handle = ${'$'}process.MainWindowHandle
                    break
                }
                Start-Sleep -Milliseconds 250
            }
            if (${'$'}handle -eq [IntPtr]::Zero) {
                throw "PPSSPP main window was not found within 30 seconds."
            }

            [LibfdxPpssppCapture]::ShowWindow(${'$'}handle, 9) | Out-Null
            [LibfdxPpssppCapture]::SetForegroundWindow(${'$'}handle) | Out-Null
            ${'$'}shell = New-Object -ComObject WScript.Shell
            for (${'$'}attempt = 0; ${'$'}attempt -lt 20; ${'$'}attempt++) {
                if (${'$'}shell.AppActivate(${'$'}process.Id)) {
                    break
                }
                Start-Sleep -Milliseconds 250
            }
            Start-Sleep -Seconds ${'$'}DelaySeconds

            New-Item -ItemType Directory -Force -Path (Split-Path -Parent ${'$'}Capture) | Out-Null
            Remove-Item -LiteralPath ${'$'}Capture -ErrorAction SilentlyContinue
            ${'$'}screenshot = ${'$'}null
            for (${'$'}screenshotAttempt = 1; ${'$'}screenshotAttempt -le 3 -and ${'$'}screenshot -eq ${'$'}null; ${'$'}screenshotAttempt++) {
                [LibfdxPpssppCapture]::SetForegroundWindow(${'$'}handle) | Out-Null
                ${'$'}shell.AppActivate(${'$'}process.Id) | Out-Null
                Start-Sleep -Milliseconds 500
                ${'$'}menuCommandSent = [LibfdxPpssppCapture]::SendMenuCommandByText(${'$'}handle, "Take Screenshot")
                if (${'$'}menuCommandSent) {
                    Write-Host "Sent PPSSPP Take Screenshot menu command"
                }
                else {
                    Write-Host "PPSSPP Take Screenshot menu command was not found"
                }
                Start-Sleep -Milliseconds 500
                ${'$'}shell.SendKeys("{F12}")
                Start-Sleep -Milliseconds 500
                [LibfdxPpssppCapture]::keybd_event(0x7B, 0, 0, [UIntPtr]::Zero)
                Start-Sleep -Milliseconds 100
                [LibfdxPpssppCapture]::keybd_event(0x7B, 0, 2, [UIntPtr]::Zero)
                Write-Host "Requested PPSSPP screenshot, attempt ${'$'}screenshotAttempt"
                ${'$'}shotDeadline = (Get-Date).AddSeconds(5)
                while ((Get-Date) -lt ${'$'}shotDeadline -and ${'$'}screenshot -eq ${'$'}null) {
                    ${'$'}matches = @()
                    foreach (${'$'}dir in ${'$'}screenshotDirs) {
                        if (Test-Path -LiteralPath ${'$'}dir) {
                            foreach (${'$'}pattern in @('*.png', '*.jpg', '*.jpeg', '*.bmp')) {
                                ${'$'}matches += @(Get-ChildItem -LiteralPath ${'$'}dir -Filter ${'$'}pattern -File -ErrorAction SilentlyContinue |
                                    Where-Object { ${'$'}_.LastWriteTimeUtc -ge ${'$'}captureStartedAt })
                            }
                        }
                    }
                    ${'$'}screenshot = ${'$'}matches | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
                    if (${'$'}screenshot -eq ${'$'}null) {
                        Start-Sleep -Milliseconds 250
                    }
                }
            }
            if (${'$'}screenshot -eq ${'$'}null) {
                throw "PPSSPP did not write a screenshot. Watched directories: ${'$'}(${'$'}screenshotDirs -join ', ')"
            }
            Copy-Item -LiteralPath ${'$'}screenshot.FullName -Destination ${'$'}Capture -Force
            Write-Host "Captured PPSSPP screenshot: ${'$'}Capture"
        }
        finally {
            ${'$'}process.Refresh()
            if (!${'$'}process.HasExited) {
                if (${'$'}process.CloseMainWindow()) {
                    if (!${'$'}process.WaitForExit(2000)) {
                        ${'$'}process.Kill()
                    }
                }
                else {
                    ${'$'}process.Kill()
                }
            }
        }
        """.trimIndent())
    return script
}

fun copyTree(sourceRoot: Path, outputRoot: Path) {
    val normalizedOutputRoot = outputRoot.toAbsolutePath().normalize()
    Files.walk(sourceRoot).use { stream ->
        stream.filter(Files::isRegularFile).toList().forEach { source ->
            val output = normalizedOutputRoot.resolve(sourceRoot.relativize(source)).normalize()
            if (!output.startsWith(normalizedOutputRoot)) {
                throw GradleException("Refusing to copy outside output root: $source")
            }
            Files.createDirectories(output.parent)
            Files.copy(source, output, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

fun unzip(zip: Path, outputDir: Path) {
    ZipInputStream(Files.newInputStream(zip)).use { input ->
        var entry = input.nextEntry
        while (entry != null) {
            val output = outputDir.resolve(entry.name).normalize()
            if (!output.startsWith(outputDir)) {
                throw GradleException("Refusing to extract outside output directory: ${entry.name}")
            }
            if (entry.isDirectory) {
                Files.createDirectories(output)
            }
            else {
                Files.createDirectories(output.parent)
                Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING)
            }
            entry = input.nextEntry
        }
    }
}

fun deleteTree(root: Path) {
    if (!Files.exists(root)) {
        return
    }
    Files.walk(root).use { stream ->
        stream.toList().sortedByDescending { it.nameCount }.forEach(Files::deleteIfExists)
    }
}

listOf(
        PspVariant("cube", "test_cube_generate", "test_cube_build", "test_cube_ppsspp_capture",
                "io.github.libfdx.tests.psp.PspCubeTestLauncher", "libfdx-tests-psp-cube", "cube test"),
        PspVariant("spritebatch", "test_spritebatch_generate", "test_spritebatch_build",
                "test_spritebatch_ppsspp_capture",
                "io.github.libfdx.tests.psp.PspSpriteBatchTestLauncher", "libfdx-psp-spritebatch",
                "SpriteBatch test"),
        PspVariant("backend_clear", "test_backend_clear_generate", "test_backend_clear_build",
                "test_backend_clear_ppsspp_capture",
                "io.github.libfdx.tests.psp.PspBackendClearTestLauncher", "libfdx-psp-backend-clear",
                "ApplicationBackend clear-only test"),
        PspVariant("backend_shape", "test_backend_shape_generate", "test_backend_shape_build",
                "test_backend_shape_ppsspp_capture",
                "io.github.libfdx.tests.psp.PspBackendShapeTestLauncher", "libfdx-psp-backend-shape",
                "ApplicationBackend shape-only test"),
        PspVariant("backend_ui_panel", "test_backend_ui_panel_generate", "test_backend_ui_panel_build",
                "test_backend_ui_panel_ppsspp_capture",
                "io.github.libfdx.tests.psp.PspBackendUiPanelTestLauncher", "libfdx-psp-backend-ui-panel",
                "ApplicationBackend UIKit panel-only test"),
        PspVariant("backend_spritebatch", "test_backend_spritebatch_generate", "test_backend_spritebatch_build",
                "test_backend_spritebatch_ppsspp_capture",
                "io.github.libfdx.tests.psp.PspBackendSpriteBatchTestLauncher", "libfdx-psp-backend2d",
                "ApplicationBackend asset SpriteBatch test"),
        PspVariant("backend_input", "test_backend_input_generate", "test_backend_input_build",
                "test_backend_input_ppsspp_capture",
                "io.github.libfdx.tests.psp.PspBackendInputTestLauncher", "libfdx-psp-backend-input",
                "ApplicationBackend input test"),
        PspVariant("backend_uikit", "test_backend_uikit_generate", "test_backend_uikit_build",
                "test_backend_uikit_ppsspp_capture",
                "io.github.libfdx.tests.psp.PspBackendUiKitTestLauncher", "libfdx-psp-backend-uikit",
                "ApplicationBackend UIKit test"),
        PspVariant("backend_uikit_smoke", "test_backend_uikit_smoke_generate", "test_backend_uikit_smoke_build",
                "test_backend_uikit_smoke_ppsspp_capture",
                "io.github.libfdx.tests.psp.PspBackendUiKitSmokeTestLauncher",
                "libfdx-psp-backend-uikit-smoke", "scripted ApplicationBackend UIKit smoke test")
).forEach(::registerPspVariant)
