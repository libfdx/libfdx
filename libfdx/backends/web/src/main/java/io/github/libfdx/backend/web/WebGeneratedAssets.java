package io.github.libfdx.backend.web;

import org.teavm.platform.metadata.ResourceArray;

/**
 * Represents a web generated assets.
 *
 * @author xpenatan
 */
public final class WebGeneratedAssets {
    private WebGeneratedAssets() {
    }

    /**
     * Returns the assets.
     *
     * @return the assets
     */
    public static native ResourceArray<WebGeneratedAsset> assets();
}
