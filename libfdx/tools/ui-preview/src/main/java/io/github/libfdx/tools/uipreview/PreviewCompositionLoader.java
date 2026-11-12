package io.github.libfdx.tools.uipreview;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

/** Fresh application classes with shared SDK/API identity. Retired loaders are closed with their canvas. */
final class PreviewCompositionLoader extends URLClassLoader {
    private final Set<String> owned = new HashSet<>();

    PreviewCompositionLoader(List<String> classpath, ClassLoader parent) throws IOException {
        super(urls(classpath), parent);
        try {
            for (String entry : classpath) {
                Path path = Path.of(entry);
                if (Files.isDirectory(path)) {
                    try (var files = Files.walk(path)) {
                        files.filter(file -> file.toString().endsWith(".class")).forEach(file ->
                                add(path.relativize(file).toString().replace('\\', '/')));
                    }
                } else {
                    try (var jar = new JarFile(path.toFile(), false, JarFile.OPEN_READ, Runtime.version());
                         var files = jar.versionedStream()) {
                        files.filter(file -> file.getName().endsWith(".class")).forEach(file -> add(file.getName()));
                    }
                }
            }
        } catch (IOException | RuntimeException failure) {
            try { close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private void add(String path) {
        if (!path.startsWith("META-INF/") && !path.equals("module-info.class")) {
            owned.add(path.substring(0, path.length() - 6).replace('/', '.'));
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> type = findLoadedClass(name);
            if (type == null) type = owned.contains(name) ? findClass(name) : super.loadClass(name, false);
            if (resolve) resolveClass(type);
            return type;
        }
    }

    private static URL[] urls(List<String> paths) throws IOException {
        URL[] result = new URL[paths.size()];
        for (int i = 0; i < result.length; i++) result[i] = Path.of(paths.get(i)).toUri().toURL();
        return result;
    }

}
