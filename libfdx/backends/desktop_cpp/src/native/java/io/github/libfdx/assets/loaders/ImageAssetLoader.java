package io.github.libfdx.assets.loaders;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.assets.AssetLoader;
import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.backend.desktopcpp.DesktopCppImageDecoder;
import io.github.libfdx.core.FdxFuture;
import java.util.Objects;

/** Native desktop implementation installed on the translation classpath. */
public final class ImageAssetLoader implements AssetLoader<ImageData> {
    private final ImageDecoder decoder;

    public ImageAssetLoader() {
        decoder = ImageAssetLoader::decodeAsync;
    }

    public ImageAssetLoader(ImageDecoder decoder) {
        this.decoder = Objects.requireNonNull(decoder);
    }

    public static void register(AssetManager assets) {
        assets.registerLoader(ImageData.class, new ImageAssetLoader());
    }

    @Override
    public Class<ImageData> type() {
        return ImageData.class;
    }

    @Override
    public FdxFuture<ImageData> load(
            AssetLoadContext context, AssetDescriptor<ImageData> descriptor) {
        FdxFuture<ImageData> result = FdxFuture.pending();
        context.readBytes(context.files().internal(descriptor.path()))
                .onSuccess(
                        bytes ->
                                context.asyncFuture(
                                                () -> decoder.decodeAsync(descriptor.path(), bytes))
                                        .onSuccess(result::complete)
                                        .onFailure(result::completeExceptionally))
                .onFailure(result::completeExceptionally);
        return result;
    }

    public static ImageData decode(byte[] bytes) {
        return decode(null, bytes);
    }

    public static ImageData decodeRawPng(byte[] bytes) {
        return PngRgbaDecoder.decode(bytes);
    }

    public static ImageData decode(String path, byte[] bytes) {
        ImageData png = decodeRawPng(bytes);
        return png != null ? png : DesktopCppImageDecoder.decode(bytes);
    }

    public static FdxFuture<ImageData> decodeAsync(String path, byte[] bytes) {
        try {
            return FdxFuture.completed(decode(path, bytes));
        } catch (RuntimeException | Error error) {
            return FdxFuture.failed(error);
        }
    }
}
