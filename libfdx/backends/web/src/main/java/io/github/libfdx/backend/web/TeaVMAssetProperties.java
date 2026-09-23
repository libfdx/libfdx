package io.github.libfdx.backend.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ArrayList;
import java.nio.file.Path;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Represents a tea VM asset properties.
 *
 * @author xpenatan
 */
public final class TeaVMAssetProperties {
    public static final String COUNT_PROPERTY = "libfdx.web.assets.count";
    public static final String ENTRY_PROPERTY_PREFIX = "libfdx.web.assets.";
    private static final String RUNTIME_PREFIX = "libfdx.web.runtimeClasspath.";
    private static final String OUTPUT_PROPERTY = "libfdx.web.outputDirectory";

    private TeaVMAssetProperties() {
    }

    /**
     * Runs the to map step.
     *
     * @param assets the assets
     * @return the to map
     */
    public static Map<String, String> toMap(List<WebAsset> assets) {
        LinkedHashMap<String, String> properties = new LinkedHashMap<>();
        properties.put(COUNT_PROPERTY, Integer.toString(assets.size()));
        for (int index = 0; index < assets.size(); index++) {
            WebAsset asset = assets.get(index);
            properties.put(ENTRY_PROPERTY_PREFIX + index + ".path", asset.getPath());
            properties.put(ENTRY_PROPERTY_PREFIX + index + ".size", Long.toString(asset.getSize()));
        }
        return properties;
    }

    /**
     * Runs the put into step.
     *
     * @param target the target value
     * @param assets the assets
     */
    public static void putInto(Properties target, List<WebAsset> assets) {
        toMap(assets).forEach(target::setProperty);
    }

    /** Build-time inputs only; paths are never embedded in the application or deployed HTML. */
    public static void putRuntimeClasspath(Properties target, List<Path> runtimeClasspath, Path outputDirectory) {
        target.setProperty(OUTPUT_PROPERTY, outputDirectory.toAbsolutePath().normalize().toString());
        target.setProperty(RUNTIME_PREFIX + "count", Integer.toString(runtimeClasspath.size()));
        for (int index = 0; index < runtimeClasspath.size(); index++) {
            target.setProperty(RUNTIME_PREFIX + index, runtimeClasspath.get(index).toAbsolutePath().normalize().toString());
        }
    }

    static List<Path> runtimeClasspath(Properties properties) {
        int count = Integer.parseInt(properties.getProperty(RUNTIME_PREFIX + "count", "0"));
        List<Path> paths = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            paths.add(Path.of(properties.getProperty(RUNTIME_PREFIX + index)));
        }
        return paths;
    }

    static String compilerIdentity(Properties properties) {
        String output = properties.getProperty(OUTPUT_PROPERTY);
        try {
            return WebAppWriter.shaderCompilerIdentity(output == null ? null : Path.of(output), runtimeClasspath(properties));
        } catch (IOException error) {
            throw new UncheckedIOException("Could not fingerprint the packaged web shader compiler", error);
        }
    }
}
