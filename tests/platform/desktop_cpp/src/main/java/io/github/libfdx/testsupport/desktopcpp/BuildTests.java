package io.github.libfdx.testsupport.desktopcpp;

import com.github.xpenatan.jnative.BuildLog;
import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.StackTraceMode;
import io.github.libfdx.backend.desktopcpp.NativeBuilder;
import io.github.libfdx.tests.desktopcpp.DesktopCppTestLauncher;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Host-side build entry point; this class is not translated. */
public final class BuildTests {
    public static void main(String[] args) throws Exception {
        if (args.length == 0
                || !java.util.Set.of("generate", "build", "run", "verify").contains(args[0])) {
            throw new IllegalArgumentException("Expected generate, build, run or verify");
        }
        if (args[0].equals("run") && args.length != 3) {
            throw new IllegalArgumentException("Expected run <gl|vulkan> <frames>");
        }
        BuildType buildType =
                BuildType.valueOf(
                        System.getProperty("libfdx.native.buildType", "DEBUG")
                                .toUpperCase(Locale.ROOT));
        var builder =
                NativeBuilder.desktop(Path.of(System.getProperty("libfdx.native.buildRoot")))
                        .classpathFromCurrentJvm()
                        .mainClass(DesktopCppTestLauncher.class.getName())
                        .targetFileName("libfdx-tests-desktop-cpp")
                        .buildType(buildType)
                        .stackTraces(
                                buildType == BuildType.DEBUG
                                        ? StackTraceMode.JAVA
                                        : StackTraceMode.NONE)
                        .javaSourceLocations(buildType == BuildType.DEBUG)
                        .timeout(Duration.ofMinutes(15))
                        .log(BuildLog.console());
        String generator = System.getProperty("libfdx.native.generator", "");
        if (!generator.isEmpty()) {
            builder.generator(generator);
        }
        var generated = builder.generate();
        System.out.println("Generated C++: " + generated.request().generatedSourcesDirectory());
        if (args[0].equals("generate")) return;
        var compiled = builder.compile(generated);
        System.out.println("Executable: " + compiled.executable());
        if (args[0].equals("run")) {
            run(
                    compiled.executable(),
                    args[1],
                    System.getProperty("libfdx.native.test", "selector"),
                    args[2]);
        } else if (args[0].equals("verify")) {
            IllegalStateException failures = null;
            for (String api : java.util.List.of("gl", "vulkan")) {
                try {
                    run(compiled.executable(), api, "auto", "0");
                } catch (IllegalStateException failure) {
                    if (failures == null) {
                        failures = new IllegalStateException("Native suite verification failed");
                    }
                    failures.addSuppressed(failure);
                }
            }
            if (failures != null) throw failures;
        }
    }

    private static void run(Path executable, String api, String test, String frames)
            throws Exception {
        int frameCount = Integer.parseInt(frames);
        if (frameCount < 0) throw new IllegalArgumentException("Frame count must be nonnegative");
        if (!api.equals("gl") && !api.equals("vulkan"))
            throw new IllegalArgumentException("Expected gl or vulkan");
        var command = new ArrayList<String>();
        command.add(executable.toString());
        command.add(api);
        command.add(test);
        command.add(frames);
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith("libfdx.test."))
                command.add("-D" + name + "=" + System.getProperty(name));
        }
        Process process =
                new ProcessBuilder(command)
                        .directory(Path.of(System.getProperty("libfdx.native.assets")).toFile())
                        .inheritIO()
                        .start();
        try {
            if (frameCount == 0 && !test.equals("auto")) process.waitFor();
            else if (!process.waitFor(test.equals("auto") ? 1200 : 120, TimeUnit.SECONDS))
                throw new IllegalStateException("Native tests timed out: " + api + " " + test);
            if (process.exitValue() != 0)
                throw new IllegalStateException(
                        "Native tests exited with "
                                + process.exitValue()
                                + ": "
                                + api
                                + " "
                                + test);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
