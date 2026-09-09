package io.github.libfdx.assets.loaders;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.assets.AssetLoader;
import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.json.JsonValue;

/**
 * Loads json asset data.
 *
 * @author xpenatan
 */
public final class JsonAssetLoader implements AssetLoader<JsonValue> {
    /**
     * Runs the register step.
     *
     * @param assets the assets
     */
    public static void register(AssetManager assets) {
        assets.registerLoader(JsonValue.class, new JsonAssetLoader());
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    @Override
    public Class<JsonValue> type() {
        return JsonValue.class;
    }

    /**
     * Loads the requested resource.
     *
     * @param context the context
     * @param descriptor the descriptor
     * @return the created value
     */
    @Override
    public FdxFuture<JsonValue> load(final AssetLoadContext context, final AssetDescriptor<JsonValue> descriptor) {
        FdxFuture<JsonValue> result = FdxFuture.pending();
        context.readBytes(context.files().internal(descriptor.path()))
                .onSuccess(bytes -> context.async(() -> new JsonReader().parse(bytes))
                        .onSuccess(result::complete).onFailure(result::completeExceptionally))
                .onFailure(result::completeExceptionally);
        return result;
    }
}
