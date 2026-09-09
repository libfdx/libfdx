package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;

/**
 * Owns three immutable uploaded environment textures in one graphics resource domain.
 * Create/dispose on the graphics thread. Environments borrow this resource; it must outlive their
 * recorded draws. Prepared CPU data is not retained. Disposal is idempotent; access after disposal fails.
 */
public final class ImageBasedLighting3D implements Disposable {
    private final Texture specular, diffuse, brdf;
    private boolean disposed;

    private ImageBasedLighting3D(Texture specular, Texture diffuse, Texture brdf) {
        this.specular = specular; this.diffuse = diffuse; this.brdf = brdf;
    }

    /** Whether this context supports the standard PBR IBL path, including filtered HDR mips and 12 sampler slots. */
    public static boolean isSupported(GraphicsContext graphics) {
        if (graphics == null || !PbrShaderProvider.usesGpuPbrShader(graphics.providerId().value())) return false;
        GraphicsCapabilities caps = graphics.device().capabilities();
        return caps.supportsColorFormat(TextureFormat.RGBA16_FLOAT)
                && caps.supportsColorFiltering(TextureFormat.RGBA16_FLOAT)
                && caps.supports(GraphicsFeature.TEXTURE_MIP_LEVELS)
                && caps.limits().maxSampledTexturesPerStage() >= 12 && caps.limits().maxSamplersPerStage() >= 12;
    }

    /** Uploads all prepared levels now. Fails explicitly when unsupported and disposes partial uploads on failure. */
    public static ImageBasedLighting3D create(GraphicsContext graphics, ImageBasedLightingData data) {
        if (data == null) throw new FdxException("Prepared IBL data cannot be null");
        if (!isSupported(graphics)) throw new FdxException("Image-based lighting requires GPU PBR, filtered RGBA16_FLOAT mips and 12 samplers");
        GraphicsDevice device = graphics.device();
        Texture[] owned = new Texture[3];
        try {
            owned[0] = device.createTexture(descriptor("IBL specular", data.specularWidth(), data.specularHeight())
                    .mipLevelCount(data.specularLevelCount()).filters(TextureFilter.LINEAR, TextureFilter.LINEAR, TextureMipmapFilter.LINEAR));
            device.writeTextureMipLevels(owned[0], data.specularPixels());
            owned[1] = device.createTexture(descriptor("IBL diffuse", data.diffuseWidth(), data.diffuseHeight()));
            device.writeTexture(owned[1], data.diffusePixels());
            owned[2] = device.createTexture(descriptor("IBL BRDF", data.brdfSize(), data.brdfSize()).wrap(TextureWrap.CLAMP_TO_EDGE));
            device.writeTexture(owned[2], data.brdfPixels());
            return new ImageBasedLighting3D(owned[0], owned[1], owned[2]);
        } catch (RuntimeException | Error failure) {
            for (Texture texture : owned) if (texture != null) {
                try { texture.dispose(); } catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    private static TextureDescriptor descriptor(String label, int width, int height) {
        return TextureDescriptor.rgba8(label, width, height).format(TextureFormat.RGBA16_FLOAT)
                .filter(TextureFilter.LINEAR).wrap(TextureWrap.REPEAT, TextureWrap.CLAMP_TO_EDGE);
    }
    /** Borrowed complete specular chain; do not rewrite or dispose separately. */
    public Texture specularTexture() { checkAlive(); return specular; }
    /** Borrowed diffuse irradiance/pi map; do not rewrite or dispose separately. */
    public Texture diffuseTexture() { checkAlive(); return diffuse; }
    /** Borrowed split-sum BRDF lookup; do not rewrite or dispose separately. */
    public Texture brdfTexture() { checkAlive(); return brdf; }
    public boolean isDisposed() { return disposed; }
    private void checkAlive() { if (disposed) throw new FdxException("Image-based lighting resource is disposed"); }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        try { specular.dispose(); } finally { try { diffuse.dispose(); } finally { brdf.dispose(); } }
    }
}
