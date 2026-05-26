package io.github.libfdx.backend.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class TeaVMAssetProperties {
    public static final String COUNT_PROPERTY = "libfdx.web.assets.count";
    public static final String ENTRY_PROPERTY_PREFIX = "libfdx.web.assets.";

    private TeaVMAssetProperties() {
    }

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

    public static void putInto(Properties target, List<WebAsset> assets) {
        toMap(assets).forEach(target::setProperty);
    }
}
