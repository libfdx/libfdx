package io.github.libfdx.graphics.internal;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Texture;
import java.nio.ByteBuffer;

/** Shared validation for complete, tightly packed uncompressed color uploads. */
public final class TextureUploads {
    private TextureUploads() { }

    public static void validate(Texture texture, ByteBuffer[] levels) {
        validateTarget(texture);
        if (levels == null || levels.length != texture.mipLevelCount()) {
            throw new FdxException("Texture upload must supply every allocated mip level");
        }
        for (int i = 0; i < levels.length; i++) validateLevel(texture, levels[i], i);
    }

    public static void validateSingle(Texture texture, ByteBuffer data) {
        validateTarget(texture);
        if (texture.mipLevelCount() != 1) throw new FdxException("Use writeTextureMipLevels to replace the complete mip chain");
        validateLevel(texture, data, 0);
    }

    private static void validateTarget(Texture texture) {
        if (texture == null || texture.isDisposed() || texture.sampleCount() != 1 || !texture.format().isColor()) {
            throw new FdxException("Texture upload requires a live single-sample color texture");
        }
    }

    private static void validateLevel(Texture texture, ByteBuffer data, int level) {
        int count = byteCount(texture, level);
        if (data == null || data.remaining() < count) {
            throw new FdxException("Texture upload data is too small for mip level " + level);
        }
    }

    public static int byteCount(Texture texture, int level) {
        long count = (long)texture.mipWidth(level) * texture.mipHeight(level) * texture.format().bytesPerPixel();
        if (count > Integer.MAX_VALUE) throw new FdxException("Texture upload exceeds buffer size limit");
        return (int)count;
    }
}
