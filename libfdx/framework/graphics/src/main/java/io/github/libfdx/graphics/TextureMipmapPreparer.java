package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxFuture;
import java.nio.ByteBuffer;

/** Optional CPU strategy for {@link TextureMipmaps#rgba8}. Borrows the source range unchanged
 * until completion; returns owned buffers positioned at zero with the same filtering semantics.
 * No GPU access is permitted. Callbacks follow the strategy's execution contract; managed loaders
 * deliver them through their asset context. The application owns the strategy's lifetime. */
@FunctionalInterface
public interface TextureMipmapPreparer {
    FdxFuture<ByteBuffer[]> prepare(ByteBuffer source, int width, int height, boolean srgb, boolean alphaWeighted);
}
