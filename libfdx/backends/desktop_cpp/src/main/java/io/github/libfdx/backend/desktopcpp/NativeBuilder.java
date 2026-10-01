package io.github.libfdx.backend.desktopcpp;

import com.github.xpenatan.jnative.CompilerException;
import com.github.xpenatan.jnative.CrashReportMode;
import com.github.xpenatan.jnative.NativeSymbols;

import io.github.libfdx.backend.cppshared.NativeResources;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Configures native compilation for the desktop C++ backend. */
public final class NativeBuilder {
    private NativeBuilder() {}

    /**
     * Creates a compiler builder with the desktop backend's packaged native sources. Add the
     * desired graphics platform dependencies to the calling application's classpath.
     *
     * @param buildRoot the application's module build directory
     * @return a builder ready for the application's classpath and main class
     */
    public static com.github.xpenatan.jnative.NativeBuilder desktop(Path buildRoot) {
        var builder =
                com.github.xpenatan.jnative.NativeBuilder.create()
                        .buildRoot(buildRoot)
                        .nativeSymbols(NativeSymbols.NONE)
                        .crashReports(CrashReportMode.OFF);
        String generator = System.getenv().getOrDefault("CMAKE_GENERATOR", "");
        if (generator.isEmpty() && System.getProperty("os.name").startsWith("Windows")) {
            generator = "Visual Studio 18 2026";
        }
        builder.generator(generator);
        try {
            Path classes = buildRoot.resolve("native-classes");
            String replacement = "io/github/libfdx/assets/loaders/ImageAssetLoader.class";
            Path target = classes.resolve(replacement);
            try (var input =
                    NativeBuilder.class.getResourceAsStream(
                            "/libfdx-native-classes/" + replacement)) {
                if (input == null)
                    throw new IOException("Missing native image loader: " + replacement);
                Files.createDirectories(target.getParent());
                Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            builder.classpath(classes);
            NativeResources.register(builder, buildRoot, NativeBuilder.class.getClassLoader());
            registerRuntime(builder, buildRoot);
            registerAudio(builder, buildRoot);
        } catch (IOException error) {
            throw new CompilerException("Could not load desktop C++ native resources", error);
        }
        return builder;
    }

    private static void registerRuntime(
            com.github.xpenatan.jnative.NativeBuilder builder, Path buildRoot) throws IOException {
        String os = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(java.util.Locale.ROOT);
        String classifier;
        String name;
        if (os.contains("windows")) {
            classifier = "windows-x64";
            name = "fdx.dll";
        } else if (os.contains("mac") || os.contains("darwin")) {
            classifier =
                    arch.contains("aarch64") || arch.contains("arm64")
                            ? "macos-arm64"
                            : "macos-x64";
            name = "libfdx.dylib";
        } else if (os.contains("linux") && (arch.equals("amd64") || arch.equals("x86_64"))) {
            classifier = "linux-x64";
            name = "libfdx.so";
        } else {
            throw new IOException("Unsupported runtime fdx host: " + os + " " + arch);
        }
        String resource = "/libfdx-native/desktop/" + classifier + "/" + name;
        try (var input = NativeBuilder.class.getResourceAsStream(resource)) {
            if (input == null)
                throw new IOException("Missing packaged runtime fdx library: " + resource);
            Path target = buildRoot.resolve("native-inputs").resolve(name);
            Files.createDirectories(target.getParent());
            Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            builder.nativeFile(target);
        }
    }

    private static void registerAudio(
            com.github.xpenatan.jnative.NativeBuilder builder, Path buildRoot) throws IOException {
        String os = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(java.util.Locale.ROOT);
        String platform;
        String name;
        if (os.contains("windows")) {
            platform = "windows/x64";
            name = "OpenAL.dll";
        } else if (os.contains("mac") || os.contains("darwin")) {
            platform =
                    arch.contains("aarch64") || arch.contains("arm64")
                            ? "macos/arm64"
                            : "macos/x64";
            name = "libopenal.dylib";
        } else if (os.contains("linux") && (arch.equals("amd64") || arch.equals("x86_64"))) {
            platform = "linux/x64";
            name = "libopenal.so";
        } else {
            throw new IOException("Unsupported OpenAL host: " + os + " " + arch);
        }
        String resource = "/" + platform + "/org/lwjgl/openal/" + name;
        try (var input = NativeBuilder.class.getResourceAsStream(resource)) {
            if (input == null)
                throw new IOException("Missing packaged OpenAL library: " + resource);
            Path target = buildRoot.resolve("native-inputs").resolve(name);
            Files.createDirectories(target.getParent());
            Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            builder.nativeFile(target);
        }
    }
}
