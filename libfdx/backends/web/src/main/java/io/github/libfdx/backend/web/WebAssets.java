package io.github.libfdx.backend.web;

import java.io.IOException;
import java.io.InputStream;
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
    public static final String DEFAULT_PRELOAD_LOGO_PATH = "fdx_logo_dark.png";
    public static final int DEFAULT_PRELOAD_LOGO_SIZE = 13459;

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
        addDefaultPreloadLogo(assets);
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
            Path output = outputRoot.resolve(asset.getPath()).normalize();
            if (!output.startsWith(outputRoot)) {
                throw new IOException("Refusing to copy asset outside web assets directory: " + source);
            }
            Files.createDirectories(output.getParent());
            if (source != null) {
                Files.copy(source, output, StandardCopyOption.REPLACE_EXISTING);
            } else {
                copyDefaultPreloadLogo(output);
            }
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

    private static void addDefaultPreloadLogo(Map<String, WebAsset> assets) {
        if (assets.containsKey(DEFAULT_PRELOAD_LOGO_PATH)) {
            return;
        }
        try (InputStream input = WebAssets.class.getClassLoader().getResourceAsStream(DEFAULT_PRELOAD_LOGO_PATH)) {
            if (input == null) {
                return;
            }
            assets.put(DEFAULT_PRELOAD_LOGO_PATH,
                    new WebAsset(DEFAULT_PRELOAD_LOGO_PATH, input.readAllBytes().length, null));
        } catch (IOException error) {
            throw new UncheckedIOException("Could not read default web preload logo", error);
        }
    }

    private static void copyDefaultPreloadLogo(Path output) throws IOException {
        try (InputStream input = WebAssets.class.getClassLoader().getResourceAsStream(DEFAULT_PRELOAD_LOGO_PATH)) {
            if (input == null) {
                throw new IOException("Default web preload logo resource was not found: " + DEFAULT_PRELOAD_LOGO_PATH);
            }
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
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
