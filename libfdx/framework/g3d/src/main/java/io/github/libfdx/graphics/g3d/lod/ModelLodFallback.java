package io.github.libfdx.graphics.g3d.lod;

/** Why the requested level was not used. */
public enum ModelLodFallback {
    /** The requested level was used. */
    NONE,
    /** Its geometry was not ready; a ready higher-detail level was used. */
    UNAVAILABLE,
    /** Its pose/material compatibility could not be established. */
    INCOMPATIBLE
}
