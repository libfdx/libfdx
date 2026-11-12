package io.github.libfdx.tools.uipreview;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Native directory notifications avoid rescanning the entire source/asset tree while idle. */
final class PreviewSourceEvents implements AutoCloseable {
    private final WatchService service;
    private final List<Path> roots;
    private final Map<WatchKey, Path> keys = new HashMap<>();
    private final Set<Path> registered = new HashSet<>();

    PreviewSourceEvents(List<String> roots) throws IOException {
        this.roots = roots.stream().map(p -> Path.of(p).toAbsolutePath().normalize()).toList();
        service = FileSystems.getDefault().newWatchService();
        try { registerRoots(); }
        catch (IOException failure) {
            try { service.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    boolean changed() throws IOException {
        boolean changed = false;
        WatchKey key;
        while ((key = service.poll()) != null) {
            Path directory = keys.get(key);
            for (var event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) { changed = true; continue; }
                Path path = directory.resolve((Path)event.context());
                if (roots.stream().noneMatch(root -> path.startsWith(root) || root.startsWith(path))) continue;
                changed = true;
                if (Files.isDirectory(path)) registerTree(path);
            }
            if (!key.reset()) {
                registered.remove(keys.remove(key));
                changed = true;
            }
        }
        if (changed) registerRoots();
        return changed;
    }

    private void registerRoots() throws IOException {
        for (Path root : roots) {
            Path parent = root.getParent();
            while (parent != null && !Files.isDirectory(parent)) parent = parent.getParent();
            if (parent != null) register(parent);
            if (Files.isDirectory(root)) registerTree(root);
        }
    }

    private void registerTree(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path directory : paths.filter(Files::isDirectory).toList()) register(directory);
        } catch (java.nio.file.NoSuchFileException removedDuringRegistration) {
            // Editors can replace directories while their contents are being enumerated.
        }
    }

    private void register(Path directory) throws IOException {
        if (registered.contains(directory)) return;
        WatchKey key = directory.register(service, StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
        keys.put(key, directory);
        registered.add(directory);
    }

    @Override
    public void close() throws IOException {
        service.close();
    }

}
