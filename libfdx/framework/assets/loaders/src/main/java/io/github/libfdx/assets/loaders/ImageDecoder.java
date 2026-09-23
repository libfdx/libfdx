package io.github.libfdx.assets.loaders;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.assets.AssetLoadContext;

/** CPU image preparation strategy borrowed by loaders. The caller keeps encoded bytes unchanged
 * until completion. Implementations return owned GC-managed pixels, never GPU resources. Managed
 * loaders marshal completion through their asset context. Strategy lifetime belongs to the caller. */
@FunctionalInterface
public interface ImageDecoder {
    /** @param path optional source path for platform caches; null for embedded images
     * @param bytes encoded input borrowed until completion */
    FdxFuture<ImageData> decodeAsync(String path, byte[] bytes);

    /** Obtains a manager-owned platform decoder on the application thread. Falls back to the
     * standard executor/cooperative decoder when no platform or ownership hook is available. */
    static ImageDecoder platformDefault(AssetLoadContext context) {
        ImageDecoder decoder = platformDecoder(context);
        return decoder != null ? decoder : ImageAssetLoader::decodeAsync;
    }

    /** Web compilation binds this hook directly to the backend's manager-owned worker. */
    private static ImageDecoder platformDecoder(AssetLoadContext context) { return null; }
}
