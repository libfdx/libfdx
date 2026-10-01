package io.github.libfdx.backend.desktopcpp;

import com.github.xpenatan.jnative.BuildLog;
import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.SourceLayout;
import com.github.xpenatan.jnative.StackTraceMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** Command-line entry point used by build integrations for jNative desktop projects. */
public final class NativeProjectTool {
    private NativeProjectTool() {}

    /**
     * Generates C++ or compiles an existing generated project from a versioned request.
     * The host JVM classpath must contain backend and provider native resources for generation.
     *
     * @param args exactly one UTF-8 properties request-file path
     * @throws IOException if the request cannot be read
     * @throws IllegalArgumentException if the request is invalid or unsupported
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one libFDX tool request-file path.");
        }
        Properties request = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8)) {
            request.load(reader);
        }
        execute(request);
    }

    static void execute(Properties request) {
        if (!"1".equals(request.getProperty("formatVersion"))) {
            throw new IllegalArgumentException("Unsupported libFDX tool request format: "
                    + request.getProperty("formatVersion"));
        }
        String action = required(request, "action");
        switch (action) {
            case "generate" -> {
                Path buildRoot = Path.of(required(request, "buildRoot"));
                String mainClass = required(request, "mainClass");
                String targetFileName = required(request, "targetFileName");
                BuildType buildType = buildType(request);
                boolean debug = booleanValue(request, "debugInformation");
                SourceLayout sourceLayout = SourceLayout.valueOf(required(request, "sourceLayout"));
                List<Path> classpath = values(request, "applicationClasspath").stream()
                        .map(Path::of).toList();
                var result = NativeBuilder.desktop(buildRoot)
                        .classpath(classpath)
                        .mainClass(mainClass)
                        .targetFileName(targetFileName)
                        .buildType(buildType)
                        .stackTraces(debug ? StackTraceMode.JAVA : StackTraceMode.NONE)
                        .javaSourceLocations(debug)
                        .sourceLayout(sourceLayout)
                        .log(BuildLog.console())
                        .generate();
                System.out.println("Generated C++: " + result.request().generatedSourcesDirectory());
            }
            case "build" -> {
                Path directory = Path.of(required(request, "projectDirectory"));
                var result = compiler(request).compileProject(directory);
                System.out.println("Executable: " + result.artifact().path());
            }
            default -> throw new IllegalArgumentException("Unsupported desktop C++ action: " + action);
        }
    }

    static com.github.xpenatan.jnative.NativeBuilder compiler(Properties request) {
        int minutes = Integer.parseInt(required(request, "buildTimeoutMinutes"));
        if (minutes <= 0) {
            throw new IllegalArgumentException("buildTimeoutMinutes must be positive");
        }
        String generator = required(request, "generator");
        if (generator.isEmpty()) {
            generator = NativeBuilder.defaultGenerator();
        }
        return com.github.xpenatan.jnative.NativeBuilder.create()
                .buildType(buildType(request))
                .cmake(required(request, "cmakeExecutable"))
                .generator(generator)
                .cmakeArgs(values(request, "cmakeArguments").toArray(String[]::new))
                .cmakeBuildArgs(values(request, "cmakeBuildArguments").toArray(String[]::new))
                .timeout(Duration.ofMinutes(minutes))
                .log(BuildLog.console());
    }

    private static BuildType buildType(Properties request) {
        return BuildType.valueOf(required(request, "buildType").toUpperCase(Locale.ROOT));
    }

    private static boolean booleanValue(Properties request, String name) {
        return switch (required(request, name)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(name + " must be true or false");
        };
    }

    private static List<String> values(Properties request, String name) {
        int count = Integer.parseInt(required(request, name + ".count"));
        if (count < 0) {
            throw new IllegalArgumentException(name + ".count must be nonnegative");
        }
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(required(request, name + "." + index));
        }
        return values;
    }

    private static String required(Properties request, String name) {
        String value = request.getProperty(name);
        if (value == null) {
            throw new IllegalArgumentException("Missing libFDX tool request property: " + name);
        }
        return value;
    }
}
