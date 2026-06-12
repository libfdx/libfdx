package io.github.libfdx.backend.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Represents a web assets.
 *
 * @author xpenatan
 */
public final class WebAssets {
    private WebAssets() {
    }

    /**
     * Runs the collect step.
     *
     * @param assetRoots the asset roots
     * @return the collect
     */
    public static List<WebAsset> collect(Collection<Path> assetRoots) {
        Objects.requireNonNull(assetRoots, "assetRoots");
        LinkedHashMap<String, WebAsset> assets = new LinkedHashMap<>();
        for (Path root : assetRoots) {
            collect(root, assets);
        }
        ArrayList<WebAsset> sorted = new ArrayList<>(assets.values());
        sorted.sort(Comparator.comparing(WebAsset::getPath));
        return List.copyOf(sorted);
    }

    /**
     * Runs the copy step.
     *
     * @param assetRoots the asset roots
     * @param assetsDirectory the assets directory
     * @return the copy
     * @throws IOException if the operation cannot be completed
     */
    public static List<WebAsset> copy(Collection<Path> assetRoots, Path assetsDirectory) throws IOException {
        Objects.requireNonNull(assetsDirectory, "assetsDirectory");
        List<WebAsset> assets = collect(assetRoots);
        deleteDirectory(assetsDirectory);
        Files.createDirectories(assetsDirectory);
        Path outputRoot = assetsDirectory.toAbsolutePath().normalize();
        for (WebAsset asset : assets) {
            Path source = asset.getSource();
            if (source == null) {
                continue;
            }
            Path output = outputRoot.resolve(asset.getPath()).normalize();
            if (!output.startsWith(outputRoot)) {
                throw new IOException("Refusing to copy asset outside web assets directory: " + source);
            }
            Files.createDirectories(output.getParent());
            Files.copy(source, output, StandardCopyOption.REPLACE_EXISTING);
        }
        return assets;
    }

    static void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path current : paths) {
                Files.deleteIfExists(current);
            }
        }
    }

    private static void collect(Path root, Map<String, WebAsset> assets) {
        Objects.requireNonNull(root, "asset root");
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (Files.isDirectory(normalizedRoot)) {
            try (Stream<Path> stream = Files.walk(normalizedRoot)) {
                stream.filter(Files::isRegularFile).forEach(file -> addDirectoryAsset(normalizedRoot, file, assets));
            } catch (IOException error) {
                throw new UncheckedIOException("Could not collect web assets from " + normalizedRoot, error);
            }
        } else if (Files.isRegularFile(normalizedRoot)) {
            addAsset(normalizedRoot.getFileName().toString(), normalizedRoot, assets);
        }
    }

    private static void addDirectoryAsset(Path root, Path file, Map<String, WebAsset> assets) {
        String relativePath = root.relativize(file).toString().replace('\\', '/');
        addAsset(relativePath, file, assets);
    }

    private static void addAsset(String relativePath, Path source, Map<String, WebAsset> assets) {
        try {
            assets.put(relativePath, new WebAsset(relativePath, Files.size(source), source));
        } catch (IOException error) {
            throw new UncheckedIOException("Could not read web asset size: " + source, error);
        }
    }
}
