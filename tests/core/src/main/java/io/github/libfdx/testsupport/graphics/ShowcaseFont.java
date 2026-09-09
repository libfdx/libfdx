package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetLease;
import io.github.libfdx.assets.AssetScope;
import io.github.libfdx.collections.ObjectMap;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.g2d.BitmapFont;
import io.github.libfdx.graphics.g2d.TextureLoadOptions;
import io.github.libfdx.ui.UiFonts;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns font leases and the optional grid wrapper; callers borrow the polled font. */
public final class ShowcaseFont implements Disposable {
    private static final Logger LOGGER = Logger.getLogger(ShowcaseFont.class.getName());
    private static final String CHARACTERS = characters();
    private final AssetScope scope;
    private final boolean allowFontFallback;
    private final AssetLease<BitmapFont> primary;
    private AssetLease<Texture> fallbackPage;
    private BitmapFont fallback;
    private Throwable primaryFailure, fallbackFailure;
    private boolean disposed;

    public ShowcaseFont(AssetScope scope, boolean allowFontFallback) {
        this.scope = scope;
        this.allowFontFallback = allowFontFallback;
        ObjectMap<String, Object> options = new ObjectMap<>();
        options.put("size", 32.0f);
        options.put("characters", CHARACTERS);
        primary = scope.load(AssetDescriptor.of(UiFonts.DEFAULT_TTF_PATH, BitmapFont.class, options));
        primary.future().onFailure(failure -> primaryFailure = failure);
    }

    /** Null means loading; a terminal failure throws unless substitution was enabled. */
    public BitmapFont poll() {
        if (disposed) throw new FdxException("Gallery font is disposed");
        if (primary.isLoaded()) return primary.asset();
        if (!primary.future().isFailed()) return null;
        Throwable cause = primaryFailure;
        if (!allowFontFallback) {
            throw new FdxException("Kinetic gallery font failed: " + primary.descriptor().path()
                    + " (allowFontFallback=false)", cause);
        }
        if (fallbackPage == null) {
            LOGGER.log(Level.WARNING, "Kinetic gallery font failed; font fallback explicitly enabled", cause);
            fallbackPage = scope.load(TextureLoadOptions.PIXEL_ART.descriptor("showcase/hud.png", Texture.class));
            fallbackPage.future().onFailure(failure -> fallbackFailure = failure);
        }
        if (fallbackPage.future().isFailed()) {
            FdxException failure = new FdxException("Kinetic gallery fallback font failed: showcase/hud.png",
                    fallbackFailure);
            failure.addSuppressed(cause);
            throw failure;
        }
        if (fallback == null && fallbackPage.isLoaded()) {
            fallback = BitmapFont.fromGrid(fallbackPage.asset(), CHARACTERS, 6, 8);
        }
        return fallback;
    }

    private static String characters() {
        StringBuilder result = new StringBuilder(64);
        for (char c = 32; c < 96; c++) result.append(c);
        return result.toString();
    }

    @Override public boolean isDisposed() { return disposed; }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        try { if (fallback != null) fallback.dispose(); }
        finally {
            try { if (fallbackPage != null) fallbackPage.dispose(); }
            finally { primary.dispose(); }
        }
    }
}
