package io.github.libfdx.backend.iosc;

import io.github.libfdx.backend.cshared.BuilderException;

/**
 * Selects the generated iOS C graphics host.
 *
 * @author xpenatan
 */
public enum IosCGraphicsApi {
    /**
     * Native iOS OpenGLES through GLKit.
     */
    GLES("gles"),

    /**
     * Native iOS Metal through MetalKit.
     */
    METAL("metal");

    private final String id;

    IosCGraphicsApi(String id) {
        this.id = id;
    }

    /**
     * Returns the stable ID.
     *
     * @return the stable ID
     */
    public String id() {
        return id;
    }

    /**
     * Returns whether this API uses Metal.
     *
     * @return true when Metal is used
     */
    public boolean isMetal() {
        return this == METAL;
    }

    /**
     * Parses an API ID.
     *
     * @param value the value
     * @return the graphics API
     */
    public static IosCGraphicsApi fromId(String value) {
        String text = value != null ? value.trim() : "";
        for (IosCGraphicsApi api : values()) {
            if (api.id.equalsIgnoreCase(text) || api.name().equalsIgnoreCase(text)) {
                return api;
            }
        }
        throw new BuilderException("Unsupported iOS C graphics API: " + value);
    }
}
