package io.github.libfdx.backend.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Represents a tea VM asset properties.
 *
 * @author xpenatan
 */
public final class TeaVMAssetProperties {
    public static final String COUNT_PROPERTY = "libfdx.web.assets.count";
    public static final String ENTRY_PROPERTY_PREFIX = "libfdx.web.assets.";

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
}
