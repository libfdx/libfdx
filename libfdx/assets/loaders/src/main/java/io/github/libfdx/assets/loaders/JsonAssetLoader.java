package io.github.libfdx.assets.loaders;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.assets.AssetLoader;
import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.json.JsonValue;

public final class JsonAssetLoader implements AssetLoader<JsonValue> {
    public static void register(AssetManager assets) {
        assets.registerLoader(JsonValue.class, new JsonAssetLoader());
    }

    @Override
    public Class<JsonValue> type() {
        return JsonValue.class;
    }

    @Override
    public FdxFuture<JsonValue> load(final AssetLoadContext context, final AssetDescriptor<JsonValue> descriptor) {
        return FdxFuture.supply(() -> new JsonReader().parse(
                context.files().internal(descriptor.path()).readBytes().get()));
    }
}
