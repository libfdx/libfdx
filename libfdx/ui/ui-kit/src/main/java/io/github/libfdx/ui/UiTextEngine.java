package io.github.libfdx.ui;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.g2d.BitmapFont;
import io.github.libfdx.graphics.g2d.BitmapFontFiles;
import io.github.libfdx.graphics.g2d.BitmapFontLayout;
import io.github.libfdx.graphics.g2d.FreeTypeFontOptions;
import java.util.LinkedHashMap;
import java.util.Map;

final class UiTextEngine implements Disposable {
    private static final int LAYOUT_CACHE_LIMIT = 512;
    private static final float MIN_RASTER_SCALE = 1.0f;
    private static final float MAX_RASTER_SCALE = 4.0f;
    private static final float RASTER_SCALE_STEP = 0.25f;
    private static final String PSP_PROVIDER_ID = "psp";

    private final FileSystem files;
    private final GraphicsContext graphics;
    private final Map<String, BitmapFont> fonts = new LinkedHashMap<String, BitmapFont>();
    private final Map<String, Boolean> unavailableFonts = new LinkedHashMap<String, Boolean>();
    private final Map<String, BitmapFontLayout> layouts = new LinkedHashMap<String, BitmapFontLayout>();
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
            font = UiFont.family("Dialog", 16.0f);
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
                FreeTypeFontOptions options = freeTypeOptions(font, rasterScale).family(font.family());
                if (font.characters() != null) {
                    options = options.characters(font.characters());
                }
                return BitmapFontFiles.generateFreeType(graphics, options);
            }
        } catch (Throwable ignored) {
        }
        return null;
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

    private String key(UiFont font, float rasterScale) {
        if (font.kind() == UiFontKind.BITMAP) {
            return font.kind().name() + "|" + System.identityHashCode(font.bitmapFont());
        }
        return font.kind().name() + "|" + font.family() + "|" + font.path() + "|" + font.size() + "|"
                + font.characters() + "|" + rasterScale;
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
        clearLastResolvedFont();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
