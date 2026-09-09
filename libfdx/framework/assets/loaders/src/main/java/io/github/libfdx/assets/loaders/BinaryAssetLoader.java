package io.github.libfdx.assets.loaders;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.assets.AssetLoader;
import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.core.FdxFuture;

/** Loads shared, immutable encoded bytes through the manager's acquisition queue. */
public final class BinaryAssetLoader implements AssetLoader<byte[]> {
    public static void register(AssetManager assets) {
        assets.registerLoader(byte[].class, new BinaryAssetLoader());
    }

    @Override
    public Class<byte[]> type() {
        return byte[].class;
    }

    /** The returned bytes are manager-owned; consumers must not mutate them. */
    @Override
    public FdxFuture<byte[]> load(AssetLoadContext context, AssetDescriptor<byte[]> descriptor) {
        return context.readBytes(context.files().internal(descriptor.path()));
    }
}
