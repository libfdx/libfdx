package io.github.libfdx.backend.web;

import org.teavm.platform.metadata.Resource;

/**
 * Defines the contract for web generated asset implementations.
 *
 * @author xpenatan
 */
public interface WebGeneratedAsset extends Resource {
    /**
     * Returns the path.
     *
     * @return the get path
     */
    String getPath();

    /**
     * Returns the size.
     *
     * @return the get size
     */
    int getSize();
}
