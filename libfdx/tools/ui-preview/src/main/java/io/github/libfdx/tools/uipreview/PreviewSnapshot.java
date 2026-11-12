package io.github.libfdx.tools.uipreview;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Copies mutable classpath entries; workers never execute from a directory being recompiled. */
final class PreviewSnapshot {
    static List<String> applicationClasspath(PreviewLaunch launch, Path snapshot) {
        List<String> result = new ArrayList<>();
        List<String> original = launch.strings("classpath");
        for (String path : launch.strings("reloadClasspath")) {
            int index = original.indexOf(path);
            if (index < 0 || !Files.exists(Path.of(path))) continue;
            boolean immutable = Boolean.parseBoolean(launch.values.getProperty("classpath." + index + ".immutable", "false"));
            result.add(immutable ? path : snapshot.resolve("classpath-" + index
                    + (Files.isDirectory(Path.of(path)) ? "" : ".jar")).toString());
        }
        return result;
    }

    static List<String> relocated(List<String> paths, Path from, Path to) {
        return paths.stream().map(Path::of).map(path -> path.startsWith(from)
                ? to.resolve(from.relativize(path)).toString() : path.toString()).toList();
    }

    static List<String> copy(PreviewLaunch launch, Path directory) throws IOException {
        Files.createDirectories(directory);
        List<String> paths = launch.strings("classpath");
        ArrayList<String> result = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            Path source = Path.of(paths.get(i)).toAbsolutePath().normalize();
            if (!Files.exists(source)) continue; // Empty resource output directories need not exist.
            if (Boolean.parseBoolean(launch.values.getProperty("classpath." + i + ".immutable", "false"))) {
                result.add(source.toString());
                continue;
            }
            if (Files.isDirectory(source) && launch.session.startsWith(source)) {
                throw new IOException("Classpath directory contains the preview session: " + source);
            }
            Path destination = directory.resolve("classpath-" + i + (Files.isDirectory(source) ? "" : ".jar"));
            copyEntry(source, destination);
            result.add(destination.toString());
        }
        return result;
    }

    static List<String> copy(List<String> classpath, Path from, Path to) throws IOException {
        Files.createDirectories(to);
        ArrayList<String> result = new ArrayList<>(classpath.size());
        for (String entry : classpath) {
            Path source = Path.of(entry);
            if (source.startsWith(from)) {
                Path destination = to.resolve(from.relativize(source));
                copyEntry(source, destination);
                result.add(destination.toString());
            } else {
                result.add(entry);
            }
        }
        return result;
    }

    private static void copyEntry(Path source, Path destination) throws IOException {
        if (Files.isDirectory(source)) {
            try (var files = Files.walk(source)) {
                for (Path file : files.toList()) {
                    Path target = destination.resolve(source.relativize(file));
                    if (Files.isDirectory(file)) Files.createDirectories(target);
                    else Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } else {
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static List<String> workerCommand(PreviewLaunch launch, List<String> classpath, Path directory, long generation,
                                      List<String> jvmArguments, String main) {
        ArrayList<String> command = new ArrayList<>();
        command.add(launch.java);
        command.addAll(launch.strings("jvmArguments"));
        command.addAll(jvmArguments);
        command.add("-Dlibfdx.uiPreview.generation=" + generation);
        command.add("-Dlibfdx.uiPreview.stopFile=" + directory.resolve("stop.request"));
        command.add("-Dlibfdx.uiPreview.readyFile=" + directory.resolve("ready"));
        command.add("-D" + PreviewSessionFiles.TARGET_FILE + "=" + directory.resolve(PreviewSessionFiles.FILE_NAME));
        command.add("-D" + PreviewReloadRequest.ENABLED + "=" + launch.values.getProperty("fastReload", "false"));
        List<String> assets = launch.strings("assets");
        command.add("-Dlibfdx.uiPreview.assets.count=" + assets.size());
        for (int i = 0; i < assets.size(); i++) command.add("-Dlibfdx.uiPreview.assets." + i + "=" + assets.get(i));
        command.add("-classpath"); command.add(String.join(File.pathSeparator, classpath));
        command.add(main);
        command.addAll(launch.strings("arguments"));
        return command;
    }

    static void deleteOwned(Path session, Path directory) throws IOException {
        Path owner = session.toAbsolutePath().normalize();
        Path target = directory.toAbsolutePath().normalize();
        if (target.equals(owner) || !target.startsWith(owner)) throw new IOException("Refusing deletion outside preview snapshots: " + target);
        if (!Files.exists(target)) return;
        if (!target.toRealPath().startsWith(owner.toRealPath())) throw new IOException("Snapshot resolves outside its owner: " + target);
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

}
