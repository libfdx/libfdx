package io.github.libfdx.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class DefaultFileSystemTest {
    private static final String RESOURCE_PATH =
            "io/github/libfdx/files/classpath-resource.txt";

    @TempDir
    Path temp;

    @Test
    void readsResourcesFromTheJavaClasspath() {
        FileHandle resource = fileSystem().classpath(RESOURCE_PATH);

        assertTrue(resource.exists());
        assertEquals("libfdx-classpath-resource",
                resource.readString(StandardCharsets.UTF_8).join().trim());
    }

    @Test
    void internalFilesFallBackToTheJavaClasspath() {
        FileHandle resource = fileSystem().internal(RESOURCE_PATH);

        assertTrue(resource.exists());
        assertEquals("libfdx-classpath-resource",
                resource.readString(StandardCharsets.UTF_8).join().trim());
    }

    @Test
    void readsResourcesFromTheThreadContextClassLoader() throws Exception {
        String resourcePath = "context-loader-only/child-resource.txt";
        Path classpathRoot = temp.resolve("context-classpath");
        Path resourceFile = classpathRoot.resolve(resourcePath);
        Files.createDirectories(resourceFile.getParent());
        Files.write(resourceFile, "thread-context-resource".getBytes(StandardCharsets.UTF_8));

        Thread thread = Thread.currentThread();
        ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        try (URLClassLoader childClassLoader = new URLClassLoader(
                new URL[]{classpathRoot.toUri().toURL()},
                originalContextClassLoader)) {
            try {
                thread.setContextClassLoader(childClassLoader);

                FileHandle resource = fileSystem().classpath(resourcePath);

                assertTrue(resource.exists());
                assertEquals("thread-context-resource",
                        resource.readString(StandardCharsets.UTF_8).join());
            } finally {
                thread.setContextClassLoader(originalContextClassLoader);
            }
        }
    }

    private DefaultFileSystem fileSystem() {
        return new DefaultFileSystem(
                temp.resolve("local").toFile(),
                temp.resolve("external").toFile(),
                temp.resolve("cache").toFile())
                .classpathResourceResolver(path -> {
                    ClassLoader context = Thread.currentThread()
                            .getContextClassLoader();
                    return context != null
                            ? context.getResourceAsStream(path)
                            : null;
                });
    }
}
