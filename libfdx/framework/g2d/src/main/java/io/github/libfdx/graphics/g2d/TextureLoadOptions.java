package io.github.libfdx.graphics.g2d;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.collections.ObjectMap;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureWrap;

/**
 * Immutable GPU texture settings shared by texture, region, atlas, and map bindings. CPU
 * ImageData remains shared independently. A manager permits one configuration per
 * image path/type; conflicting settings fail instead of returning an incompatible
 * cached texture. This does not convert/premultiply pixel bytes or select shader output.
 */
public final class TextureLoadOptions {
    /** Existing raw-byte sampling behavior: linear filtering and clamped UNORM. */
    public static final TextureLoadOptions DEFAULT = new TextureLoadOptions(TextureFormat.RGBA8_UNORM,
            TextureFilter.LINEAR, TextureWrap.CLAMP_TO_EDGE);
    /** Nearest filtering for byte-preserving pixel-art sprite rendering. */
    public static final TextureLoadOptions PIXEL_ART = new TextureLoadOptions(TextureFormat.RGBA8_UNORM,
            TextureFilter.NEAREST, TextureWrap.CLAMP_TO_EDGE);
    private static final String KEY = "g2d.texture";
    private final TextureFormat format;
    private final TextureFilter filter;
    private final TextureWrap wrap;
    /**
     * Accepts RGBA8_UNORM or RGBA8_UNORM_SRGB. SRGB sampling decodes RGB in hardware;
     * only use it with a shader that expects linear samples and handles output encoding.
     * Alpha always remains linear. Data textures must use UNORM.
     */
    public TextureLoadOptions(TextureFormat format, TextureFilter filter, TextureWrap wrap) {
        if ((format != TextureFormat.RGBA8_UNORM && format != TextureFormat.RGBA8_UNORM_SRGB)
                || filter == null || wrap == null) throw new IllegalArgumentException("RGBA8 format, filter and wrap required");
        this.format = format; this.filter = filter; this.wrap = wrap;
    }
    /** Builds a typed texture/region/atlas/map request; default settings use the legacy empty options. */
    public <T> AssetDescriptor<T> descriptor(String path, Class<T> type) {
        if (type != Texture.class && type != TextureRegion.class && type != TileMapAsset.class && type != SpriteAtlas.class) {
            throw new IllegalArgumentException("Texture settings require Texture, TextureRegion, SpriteAtlas, or TileMapAsset");
        }
        if (equals(DEFAULT)) return AssetDescriptor.of(path, type);
        ObjectMap<String, Object> options = new ObjectMap<String, Object>(1);
        options.put(KEY, this);
        return AssetDescriptor.of(path, type, options.view());
    }
    static TextureLoadOptions from(AssetDescriptor<?> descriptor) {
        Object value = descriptor.options().get(KEY);
        if (value == null) return DEFAULT;
        if (!(value instanceof TextureLoadOptions)) throw new IllegalArgumentException("Invalid texture options");
        return (TextureLoadOptions) value;
    }
    /** GPU texture format, including sample transfer behavior. */
    public TextureFormat format() { return format; }
    /** Minification and magnification filter. */
    public TextureFilter filter() { return filter; }
    /** Wrap mode for both axes. */
    public TextureWrap wrap() { return wrap; }
    @Override
    public boolean equals(Object other) {
        return other instanceof TextureLoadOptions value && format == value.format && filter == value.filter && wrap == value.wrap;
    }
    @Override
    public int hashCode() { return (format.ordinal() * 31 + filter.ordinal()) * 31 + wrap.ordinal(); }
}
