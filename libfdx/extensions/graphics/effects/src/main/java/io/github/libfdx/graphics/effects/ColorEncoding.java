package io.github.libfdx.graphics.effects;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Texture;

/** Encoding of stored RGB texels; alpha is always linear.
 * SRGB formats decode in hardware. SRGB in an UNORM texture decodes in the effect.
 * Normal maps contain LINEAR vector data, never color-encoded data. */
public enum ColorEncoding {
    LINEAR, SRGB;

    boolean decode(Texture texture) {
        if (texture.format().isSrgb() && this != SRGB) {
            throw new FdxException("An sRGB texture must declare SRGB stored color encoding");
        }
        return this == SRGB && !texture.format().isSrgb();
    }
}

