package io.github.libfdx.graphics;

/** Selection between allocated mip levels. NONE samples level zero regardless of footprint. */
public enum TextureMipmapFilter {
    NONE, NEAREST, LINEAR
}
