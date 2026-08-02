package io.github.libfdx.ui;

import io.github.libfdx.collections.IdentityMap;
import io.github.libfdx.collections.OrderedMap;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.g2d.BitmapFont;
import io.github.libfdx.graphics.g2d.BitmapFontFiles;
import io.github.libfdx.graphics.g2d.BitmapFontLayout;
import io.github.libfdx.graphics.g2d.FreeTypeFontOptions;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents an ui text engine.
 *
 * @author xpenatan
 */
final class UiTextEngine implements Disposable {
    private static final Logger LOGGER = Logger.getLogger(UiTextEngine.class.getName());
    private static final int LAYOUT_CACHE_LIMIT = 512;
    private static final int FONT_KEY_CACHE_LIMIT = 512;
    private static final UiFont DEFAULT_FONT = UiFont.family("Dialog", 16.0f);
    private static final float MIN_RASTER_SCALE = 1.0f;
    private static final float MAX_RASTER_SCALE = 4.0f;
    private static final float RASTER_SCALE_STEP = 0.25f;
    private static final String PSP_PROVIDER_ID = "psp";

    private final FileSystem files;
    private final GraphicsContext graphics;
    private final OrderedMap<String, BitmapFont> fonts = new OrderedMap<String, BitmapFont>();
    private final OrderedMap<String, Boolean> unavailableFonts = new OrderedMap<String, Boolean>();
    private final OrderedMap<String, BitmapFontLayout> layouts = new OrderedMap<String, BitmapFontLayout>();
    private final IdentityMap<UiFont, CachedFontKey> fontKeys = new IdentityMap<UiFont, CachedFontKey>();
    private UiFont lastResolvedFont;
    private float lastResolvedRasterScale;
    private BitmapFont lastResolvedBitmapFont;
    private boolean lastResolvedUnavailable;
    private boolean disposed;

    UiTextEngine(FileSystem files, GraphicsContext graphics) {
        this.files = files;
        this.graphics = graphics;
    }

    BitmapFont resolve(UiFont font, float displayScale) {
        if (font == null) {
            font = DEFAULT_FONT;
        }
        if (font.kind() == UiFontKind.BITMAP) {
            return font.bitmapFont();
        }
        if ((font.kind() == UiFontKind.FAMILY || font.kind() == UiFontKind.FREETYPE_FILE) && !supportsFreeType()) {
            return null;
        }
        float rasterScale = rasterScale(font, displayScale);
        if (lastResolvedFont == font && Float.compare(lastResolvedRasterScale, rasterScale) == 0) {
            if (lastResolvedUnavailable) {
                return null;
            }
            if (lastResolvedBitmapFont == null || !lastResolvedBitmapFont.isDisposed()) {
                return lastResolvedBitmapFont;
            }
            clearLastResolvedFont();
        }
        String key = key(font, rasterScale);
        BitmapFont cached = fonts.get(key);
        if (cached != null) {
            if (!cached.isDisposed()) {
                cacheLastResolvedFont(font, rasterScale, cached, false);
                return cached;
            }
            fonts.remove(key);
        }
        if (unavailableFonts.containsKey(key)) {
            if (font.fallback() != null) {
                BitmapFont fallback = resolve(font.fallback(), displayScale);
                cacheLastResolvedFont(font, rasterScale, fallback, fallback == null);
                return fallback;
            }
            cacheLastResolvedFont(font, rasterScale, null, true);
            return null;
        }
        BitmapFont resolved = load(font, rasterScale);
        if (resolved == null) {
            unavailableFonts.put(key, Boolean.TRUE);
            if (font.fallback() != null) {
                resolved = resolve(font.fallback(), displayScale);
            }
        }
        if (resolved != null) {
            fonts.put(key, resolved);
        }
        cacheLastResolvedFont(font, rasterScale, resolved, resolved == null);
        return resolved;
    }

    BitmapFontLayout layout(String text, UiTextStyle style, float maxWidth, float displayScale) {
        UiTextStyle actualStyle = style != null ? style : UiTextStyle.text();
        BitmapFont font = resolve(actualStyle.font(), displayScale);
        if (font == null) {
            return null;
        }
        String key = layoutKey(text, actualStyle, maxWidth, displayScale);
        BitmapFontLayout cached = layouts.get(key);
        if (cached != null) {
            return cached;
        }
        BitmapFontLayout layout = font.layout(text, actualStyle.size(), maxWidth, actualStyle.wrap(),
                actualStyle.ellipsis());
        if (layouts.size() >= LAYOUT_CACHE_LIMIT) {
            layouts.clear();
        }
        layouts.put(key, layout);
        return layout;
    }

    private BitmapFont load(UiFont font, float rasterScale) {
        try {
            if (font.kind() == UiFontKind.BITMAP_FILE && files != null && graphics != null) {
                return BitmapFontFiles.loadBitmap(graphics, files, font.path());
            }
            if (font.kind() == UiFontKind.FREETYPE_FILE && files != null && graphics != null) {
                FreeTypeFontOptions options = freeTypeOptions(font, rasterScale);
                if (font.characters() != null) {
                    options = options.characters(font.characters());
                }
                return BitmapFontFiles.loadFreeType(graphics, files, font.path(), options);
            }
            if (font.kind() == UiFontKind.FAMILY && graphics != null) {
                LOGGER.log(Level.FINE, "UI font family ''{0}'' is unavailable; using the configured or built-in "
                        + "fallback", font.family());
                return null;
            }
        } catch (RuntimeException | LinkageError error) {
            LOGGER.log(Level.WARNING, "Unable to load UI font " + font.kind() + " '" + fontSource(font) + "'", error);
        }
        return null;
    }

    private String fontSource(UiFont font) {
        return font.kind() == UiFontKind.FAMILY ? font.family() : font.path();
    }

    private boolean supportsFreeType() {
        return graphics == null
                || graphics.providerId() == null
                || !PSP_PROVIDER_ID.equals(graphics.providerId().value());
    }

    private FreeTypeFontOptions freeTypeOptions(UiFont font, float rasterScale) {
        float scale = Math.max(MIN_RASTER_SCALE, rasterScale);
        int padding = Math.max(2, Math.round(2.0f * scale));
        int atlasWidth = Math.max(512, Math.round(512.0f * scale));
        return FreeTypeFontOptions.defaults(font.size() * scale).padding(padding).atlasWidth(atlasWidth);
    }

    private float rasterScale(UiFont font, float displayScale) {
        if (font == null || (font.kind() != UiFontKind.FREETYPE_FILE && font.kind() != UiFontKind.FAMILY)) {
            return MIN_RASTER_SCALE;
        }
        float scale = Math.max(MIN_RASTER_SCALE, Math.min(MAX_RASTER_SCALE, displayScale));
        return Math.max(MIN_RASTER_SCALE, (float) Math.ceil((scale - 0.001f) / RASTER_SCALE_STEP)
                * RASTER_SCALE_STEP);
    }

    String key(UiFont font, float rasterScale) {
        CachedFontKey cached = fontKeys.get(font);
        if (cached != null && Float.compare(cached.rasterScale, rasterScale) == 0) {
            return cached.value;
        }
        String value;
        if (font.kind() == UiFontKind.BITMAP) {
            value = font.kind().name() + "|" + System.identityHashCode(font.bitmapFont());
        } else {
            value = font.kind().name() + "|" + font.family() + "|" + font.path() + "|" + font.size() + "|"
                    + font.characters() + "|" + rasterScale;
        }
        if (cached == null) {
            if (fontKeys.size() >= FONT_KEY_CACHE_LIMIT) {
                fontKeys.clear();
            }
            cached = new CachedFontKey();
            fontKeys.put(font, cached);
        }
        cached.rasterScale = rasterScale;
        cached.value = value;
        return value;
    }

    private String layoutKey(String text, UiTextStyle style, float maxWidth, float displayScale) {
        float rasterScale = rasterScale(style.font(), displayScale);
        return key(style.font(), rasterScale) + "|" + style.size() + "|" + maxWidth + "|" + style.wrap() + "|"
                + style.ellipsis() + "|" + text;
    }

    private void cacheLastResolvedFont(UiFont font, float rasterScale, BitmapFont bitmapFont, boolean unavailable) {
        lastResolvedFont = font;
        lastResolvedRasterScale = rasterScale;
        lastResolvedBitmapFont = bitmapFont;
        lastResolvedUnavailable = unavailable;
    }

    private void clearLastResolvedFont() {
        lastResolvedFont = null;
        lastResolvedRasterScale = 0.0f;
        lastResolvedBitmapFont = null;
        lastResolvedUnavailable = false;
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        for (BitmapFont font : fonts.values()) {
            if (font != null && !font.isDisposed()) {
                font.dispose();
            }
        }
        fonts.clear();
        unavailableFonts.clear();
        layouts.clear();
        fontKeys.clear();
        clearLastResolvedFont();
    }

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private static final class CachedFontKey {
        private float rasterScale;
        private String value;
    }
}
