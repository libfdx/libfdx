package io.github.libfdx.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.ProviderHandle;

/**
 * Defines the contract for texture implementations.
 *
 * @author xpenatan
 */
public interface Texture extends ProviderHandle, Disposable {
    /**
     * Returns the width.
     *
     * @return the width
     */
    int width();

    /**
     * Returns the height.
     *
     * @return the height
     */
    int height();

    /**
     * Returns the format.
     *
     * @return the format
     */
    TextureFormat format();

    /**
     * Returns the usage.
     *
     * @return the usage
     */
    TextureUsage usage();

    /**
     * Returns the texture sample count.
     *
     * @return the sample count
     */
    default int sampleCount() {
        return 1;
    }

    /** Number of allocated levels, including the base. Allocation does not generate level data. */
    default int mipLevelCount() { return 1; }

    default int mipWidth(int level) {
        checkMipLevel(level);
        return Math.max(1, width() >> level);
    }

    default int mipHeight(int level) {
        checkMipLevel(level);
        return Math.max(1, height() >> level);
    }

    private void checkMipLevel(int level) {
        if (level < 0 || level >= mipLevelCount()) throw new io.github.libfdx.core.FdxException("Texture mip level outside range");
    }

    /**
     * Returns the cached borrowed level-zero attachment view. It remains valid until texture
     * disposal and does not retain ownership. Sampled texture bindings use the complete chain.
     *
     * @return the default texture view
     */
    default TextureView view() {
        throw new UnsupportedOperationException("Texture views are not supported by this provider");
    }

    /** Returns a cached borrowed attachment view of exactly one level. Its lifetime is the texture's;
     * a view does not retain ownership. Sampled texture bindings continue to address the complete chain. */
    default TextureView view(int mipLevel) {
        checkMipLevel(mipLevel);
        if (mipLevel == 0) return view();
        throw new UnsupportedOperationException("Mip attachment views are not supported by this provider");
    }
}
