package io.github.libfdx.assets.loaders;

import io.github.libfdx.assets.*;
import io.github.libfdx.core.FdxFuture;

/** Reads JSON atlas metadata, then parses on the manager's preparation executor. No graphics access. */
public final class AtlasDataLoader implements AssetLoader<AtlasData> {
    public static void register(AssetManager manager) { manager.registerLoader(AtlasData.class,new AtlasDataLoader()); }
    @Override
    public Class<AtlasData> type() { return AtlasData.class; }
    @Override
    public FdxFuture<AtlasData> load(AssetLoadContext context,AssetDescriptor<AtlasData> descriptor) {
        FdxFuture<AtlasData> result=FdxFuture.pending();
        context.readBytes(context.files().internal(descriptor.path()))
                .onSuccess(bytes -> context.async(() -> AtlasData.parse(bytes))
                        .onSuccess(result::complete).onFailure(result::completeExceptionally))
                .onFailure(result::completeExceptionally);
        return result;
    }
}
