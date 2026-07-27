package io.github.libfdx.graphics.shader.reflection;

import io.github.libfdx.core.FdxException;

/**
 * Lists storage texture formats carried by the stable FDXI schema.
 */
public enum ShaderStorageTextureFormat {
    NONE(0), R8_SNORM(1), R8_UINT(2), R8_SINT(3), RG8_UNORM(4), RG8_SNORM(5), RG8_UINT(6), RG8_SINT(7),
    R16_UNORM(8), R16_SNORM(9), R16_UINT(10), R16_SINT(11), R16_FLOAT(12), RG16_UNORM(13),
    RG16_SNORM(14), RG16_UINT(15), RG16_SINT(16), RG16_FLOAT(17), BGRA8_UNORM(18), RGBA8_UNORM(19),
    RGBA8_SNORM(20), RGBA8_UINT(21), RGBA8_SINT(22), RGBA16_UNORM(23), RGBA16_SNORM(24),
    RGBA16_UINT(25), RGBA16_SINT(26), RGBA16_FLOAT(27), R32_UINT(28), R32_SINT(29), R32_FLOAT(30),
    RG32_UINT(31), RG32_SINT(32), RG32_FLOAT(33), RGBA32_UINT(34), RGBA32_SINT(35),
    RGBA32_FLOAT(36), R8_UNORM(37), RGB10_A2_UINT(38), RGB10_A2_UNORM(39), RG11_B10_UFLOAT(40);

    private final int fdxiTag;

    ShaderStorageTextureFormat(int fdxiTag) {
        this.fdxiTag = fdxiTag;
    }

    public int fdxiTag() {
        return fdxiTag;
    }

    public static ShaderStorageTextureFormat fromFdxiTag(int tag) {
        for (ShaderStorageTextureFormat format : values()) {
            if (format.fdxiTag == tag) {
                return format;
            }
        }
        throw new FdxException("Unknown FDXI storage texture format tag: " + tag);
    }
}
