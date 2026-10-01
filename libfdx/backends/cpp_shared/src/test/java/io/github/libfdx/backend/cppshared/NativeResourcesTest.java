package io.github.libfdx.backend.cppshared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.xpenatan.jnative.NativeBuilder;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeResourcesTest {
    @TempDir Path temporary;

    @Test
    void loadsResourcesFromClassDirectoriesAndDependencyJars() throws Exception {
        Path classes = temporary.resolve("classes with spaces");
        writeResource(classes, "libfdx-native/shared/bridge.hpp", "shared bridge");
        writeResource(classes, "META-INF/libfdx/native-cpp.list",
                "# Shared resources\nlibfdx-native/shared/bridge.hpp\n");
        Path jar = writeJar("provider with spaces.jar", Map.of(
                "META-INF/libfdx/native-cpp.list", "libfdx-native/provider/binding.cpp\n",
                "libfdx-native/provider/binding.cpp", "provider binding"));
        Path build = temporary.resolve("build");
        try (var loader = loader(classes, jar)) {
            var files = NativeResources.register(NativeBuilder.create(), build, loader);
            assertEquals(Set.of("bridge.hpp", "binding.cpp"), files.stream()
                    .map(path -> path.getFileName().toString()).collect(Collectors.toSet()));
            assertTrue(files.stream().allMatch(path -> path.getParent().equals(build.resolve("native-inputs"))));
        }
        assertEquals("shared bridge", Files.readString(build.resolve("native-inputs/bridge.hpp")));
        assertEquals("provider binding", Files.readString(build.resolve("native-inputs/binding.cpp")));
    }

    @Test
    void rejectsDuplicateFileNamesBeforeWritingSources() throws Exception {
        Path first = writeJar("first.jar", Map.of(
                "META-INF/libfdx/native-cpp.list", "libfdx-native/first/binding.cpp\n",
                "libfdx-native/first/binding.cpp", "first binding"));
        Path second = writeJar("second.jar", Map.of(
                "META-INF/libfdx/native-cpp.list", "libfdx-native/second/binding.cpp\n",
                "libfdx-native/second/binding.cpp", "second binding"));
        Path build = temporary.resolve("build");
        try (var loader = loader(first, second)) {
            IOException failure = assertThrows(IOException.class,
                    () -> NativeResources.register(NativeBuilder.create(), build, loader));
            assertTrue(failure.getMessage().contains("Duplicate native resource"));
        }
        assertFalse(Files.exists(build.resolve("native-inputs")));
    }

    @Test
    void requiresResourcesToExistInTheDeclaringDependency() throws Exception {
        Path first = writeJar("first.jar", Map.of(
                "META-INF/libfdx/native-cpp.list", "libfdx-native/provider/binding.cpp\n"));
        Path second = writeJar("second.jar", Map.of(
                "libfdx-native/provider/binding.cpp", "wrong dependency"));
        try (var loader = loader(first, second)) {
            assertThrows(IOException.class, () -> NativeResources.register(
                    NativeBuilder.create(), temporary.resolve("build"), loader));
        }
    }

    @Test
    void rejectsManifestPathsOutsideNativeResources() throws Exception {
        Path jar = writeJar("invalid.jar", Map.of(
                "META-INF/libfdx/native-cpp.list", "libfdx-native/../outside.cpp\n",
                "outside.cpp", "outside resource"));
        try (var loader = loader(jar)) {
            IOException failure = assertThrows(IOException.class, () -> NativeResources.register(
                    NativeBuilder.create(), temporary.resolve("build"), loader));
            assertTrue(failure.getMessage().contains("Invalid native resource"));
        }
    }

    private Path writeJar(String name, Map<String, String> resources) throws IOException {
        Path jar = temporary.resolve(name);
        try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
            for (var resource : resources.entrySet()) {
                output.putNextEntry(new JarEntry(resource.getKey()));
                output.write(resource.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return jar;
    }

    private static void writeResource(Path root, String name, String contents) throws IOException {
        Path file = root.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
    }

    private static URLClassLoader loader(Path... paths) throws IOException {
        URL[] urls = new URL[paths.length];
        for (int i = 0; i < paths.length; ++i) {
            urls[i] = paths[i].toUri().toURL();
        }
        return new URLClassLoader(urls, null);
    }
}
