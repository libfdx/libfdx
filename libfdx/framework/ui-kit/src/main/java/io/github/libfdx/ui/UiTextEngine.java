package io.github.libfdx.ui;

import io.github.libfdx.collections.KeyComparison;
import io.github.libfdx.collections.ObjectIterator;
import io.github.libfdx.collections.ObjectMap;
import io.github.libfdx.collections.OrderedMap;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
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
    private static final UiFont DEFAULT_FONT = UiFonts.defaultFont(16.0f);
    private static final float MIN_RASTER_SCALE = 1.0f;
    private static final float MAX_RASTER_SCALE = 4.0f;
    private static final float RASTER_SCALE_STEP = 0.25f;
    private static final String PSP_PROVIDER_ID = "psp";

    private final FileSystem files;
    private final GraphicsContext graphics;
    private final OrderedMap<String, BitmapFont> fonts = new OrderedMap<String, BitmapFont>();
    private final OrderedMap<String, FontFailure> unavailableFonts = new OrderedMap<String, FontFailure>();
    private final OrderedMap<String, BitmapFontLayout> layouts = new OrderedMap<String, BitmapFontLayout>();
    private final ObjectMap<UiFont, CachedFontKey> fontKeys =
            new ObjectMap<UiFont, CachedFontKey>(KeyComparison.IDENTITY);
    private UiFont lastResolvedFont;
    private float lastResolvedRasterScale;
    private BitmapFont lastResolvedBitmapFont;
    private boolean allowFontFallback;
    private boolean disposed;

    UiTextEngine(FileSystem files, GraphicsContext graphics) {
        this.files = files;
        this.graphics = graphics;
    }

    boolean allowFontFallback() {
        return allowFontFallback;
    }

    void allowFontFallback(boolean allow) {
        allowFontFallback = allow;
        layouts.clear();
        clearLastResolvedFont();
    }

    BitmapFont resolve(UiFont font, float displayScale) {
        if (font == null) {
            font = DEFAULT_FONT;
        }
        if (font.kind() == UiFontKind.BITMAP && font.bitmapFont() != null && !font.bitmapFont().isDisposed()) {
            return font.bitmapFont();
        }
        float rasterScale = rasterScale(font, displayScale);
        if (lastResolvedFont == font && Float.compare(lastResolvedRasterScale, rasterScale) == 0) {
            if (!lastResolvedBitmapFont.isDisposed()) {
                return lastResolvedBitmapFont;
            }
            clearLastResolvedFont();
        }
        String key = key(font, rasterScale);
        BitmapFont cached = fonts.get(key);
        if (cached != null) {
            if (!cached.isDisposed()) {
                cacheLastResolvedFont(font, rasterScale, cached);
                return cached;
            }
            fonts.remove(key);
        }
        FontFailure failure = unavailableFonts.get(key);
        if (failure == null) {
            BitmapFont resolved;
            try {
                resolved = load(font, rasterScale);
            } catch (RuntimeException | LinkageError cause) {
                failure = new FontFailure(new FdxException("Unable to load UI font " + font.kind()
                        + " '" + fontSource(font) + "'", cause));
                unavailableFonts.put(key, failure);
                return fallback(font, displayScale, failure);
            }
            fonts.put(key, resolved);
            cacheLastResolvedFont(font, rasterScale, resolved);
            return resolved;
        }
        return fallback(font, displayScale, failure);
    }

    private BitmapFont fallback(UiFont font, float displayScale, FontFailure failure) {
        if (!allowFontFallback) {
            throw failure.error;
        }
        if (!failure.logged) {
            failure.logged = true;
            LOGGER.log(Level.WARNING, failure.error.getMessage() + "; font fallback explicitly enabled", failure.error);
        }
        // Never cache a substitute under the failed source's key: another UiFont
        // may select a different fallback, and bitmap fallbacks remain borrowed.
        return font.fallback() != null ? resolve(font.fallback(), displayScale) : null;
    }

    BitmapFontLayout layout(String text, UiTextStyle style, float maxWidth, float displayScale) {
        UiTextStyle actualStyle = style != null ? style : UiTextStyle.text();
        BitmapFont font = resolve(actualStyle.font(), displayScale);
        if (font == null) {
            return null;
        }
        String key = layoutKey(text, actualStyle, maxWidth, displayScale, font);
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
        if (font.kind() == UiFontKind.BITMAP_FILE) {
            return BitmapFontFiles.loadBitmap(graphics, files, font.path());
        }
        if (font.kind() == UiFontKind.FREETYPE_FILE) {
            if (!supportsFreeType()) {
                throw new FdxException("Runtime FreeType fonts are unavailable on provider " + PSP_PROVIDER_ID);
            }
            FreeTypeFontOptions options = freeTypeOptions(font, rasterScale);
            if (font.characters() != null) {
                options = options.characters(font.characters());
            }
            return BitmapFontFiles.loadFreeType(graphics, files, font.path(), options);
        }
        if (font.kind() == UiFontKind.FAMILY) {
            throw new FdxException("Portable font-family lookup is unavailable; supply a .ttf/.otf or bitmap font");
        }
        throw new FdxException("The supplied bitmap font is null or disposed");
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

    private String layoutKey(String text, UiTextStyle style, float maxWidth, float displayScale, BitmapFont resolvedFont) {
        float rasterScale = rasterScale(style.font(), displayScale);
        return key(style.font(), rasterScale) + "|" + System.identityHashCode(resolvedFont) + "|"
                + style.size() + "|" + maxWidth + "|" + style.wrap() + "|"
                + style.ellipsis() + "|" + text;
    }

    private void cacheLastResolvedFont(UiFont font, float rasterScale, BitmapFont bitmapFont) {
        lastResolvedFont = font;
        lastResolvedRasterScale = rasterScale;
        lastResolvedBitmapFont = bitmapFont;
    }

    private void clearLastResolvedFont() {
        lastResolvedFont = null;
        lastResolvedRasterScale = 0.0f;
        lastResolvedBitmapFont = null;
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
        ObjectIterator<BitmapFont> iterator = fonts.values().iterator();
        while (iterator.hasNext()) {
            BitmapFont font = iterator.next();
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

    private static final class FontFailure {
        private final FdxException error;
        private boolean logged;

        private FontFailure(FdxException error) {
            this.error = error;
        }
    }
}
