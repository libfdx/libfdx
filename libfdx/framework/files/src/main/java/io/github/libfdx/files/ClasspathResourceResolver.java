package io.github.libfdx.files;

import java.io.InputStream;

/**
 * Platform hook for resources that are not represented by ordinary files.
 */
public interface ClasspathResourceResolver {
    /**
     * Opens a normalized, leading-slash-free resource path.
     *
     * @param path resource path
     * @return the resource stream, or {@code null} when absent
     */
    InputStream open(String path);
}
