package io.github.libfdx.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.files.FileHandle;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.g2d.BitmapFont;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

public class UiTextEngineTest {
    @Test
    void missingFileFailsByDefaultWithSourceAndOriginalCause() {
        FdxException missing = new FdxException("file does not exist");
        int[] reads = {0};
        FileHandle file = proxy(FileHandle.class, (method, args) -> {
            assertEquals("readString", method);
            reads[0]++;
            return FdxFuture.failed(missing);
        });
        FileSystem files = proxy(FileSystem.class, (method, args) -> file);
        GraphicsContext graphics = proxy(GraphicsContext.class, (method, args) -> null);
        UiTextEngine engine = new UiTextEngine(files, graphics);
        BitmapFont substitute = font(5);
        UiFont requested = UiFont.bitmapFile("fonts/missing.fnt", 8).fallback(UiFont.bitmap(substitute));
        assertFalse(engine.allowFontFallback());
        FdxException failure = assertThrows(FdxException.class, () -> engine.resolve(requested, 1));
        assertTrue(failure.getMessage().contains("fonts/missing.fnt"));
        assertSame(missing, failure.getCause());
        assertSame(failure, assertThrows(FdxException.class, () -> engine.resolve(requested, 1)));
        assertEquals(1, reads[0]);

        engine.allowFontFallback(true);
        assertSame(substitute, engine.resolve(requested, 1));
        engine.allowFontFallback(false);
        assertSame(failure, assertThrows(FdxException.class, () -> engine.resolve(requested, 1)));
        engine.dispose();
        assertFalse(substitute.isDisposed());
        substitute.dispose();
    }

    @Test
    void optInFallbacksWithTheSameSourceKeepTheirOwnFontsAndLayouts() {
        UiTextEngine engine = new UiTextEngine(null, null);
        engine.allowFontFallback(true);
        BitmapFont narrow = font(5), wide = font(10);
        UiFont a = UiFont.freeType("missing.ttf", 8).fallback(UiFont.bitmap(narrow));
        UiFont b = UiFont.freeType("missing.ttf", 8).fallback(UiFont.bitmap(wide));
        UiTextStyle sa = UiTextStyle.text().font(a).size(8);
        UiTextStyle sb = UiTextStyle.text().font(b).size(8);
        assertSame(narrow, engine.resolve(a, 1));
        assertSame(wide, engine.resolve(b, 1));
        assertSame(narrow, engine.resolve(a, 1));
        assertEquals(10, engine.layout("AA", sa, 100, 1).width());
        assertEquals(20, engine.layout("AA", sb, 100, 1).width());
        engine.dispose();
        assertFalse(narrow.isDisposed());
        assertFalse(wide.isDisposed());
        narrow.dispose(); wide.dispose();
    }

    @Test
    void unsupportedProviderUsesConfiguredBitmapOnlyAfterOptIn() {
        GraphicsContext graphics = proxy(GraphicsContext.class, (method, args) -> ProviderId.of("psp"));
        UiTextEngine engine = new UiTextEngine(null, graphics);
        BitmapFont bitmap = font(5);
        UiFont requested = UiFonts.defaultFont(8).fallback(UiFont.bitmap(bitmap));
        assertThrows(FdxException.class, () -> engine.resolve(requested, 1));
        engine.allowFontFallback(true);
        assertSame(bitmap, engine.resolve(requested, 1));
        engine.dispose(); bitmap.dispose();
    }

    @Test
    void unsupportedFamilyAndDisposedBitmapAreErrorsByDefault() {
        UiTextEngine engine = new UiTextEngine(null, null);
        assertThrows(FdxException.class, () -> engine.resolve(UiFont.family("Dialog", 16), 1));
        assertThrows(FdxException.class, () -> engine.resolve(UiFont.bitmap(null), 1));
        BitmapFont bitmap = font(5);
        UiFont requested = UiFont.bitmap(bitmap);
        assertSame(bitmap, engine.resolve(requested, 1));
        bitmap.dispose();
        assertThrows(FdxException.class, () -> engine.resolve(requested, 1));
        engine.dispose();
    }

    @Test
    void rootRequiresOptInForBuiltInDiagnosticTextAndCanDisableItAgain() {
        UiRoot root = new UiRoot(null, null, null, null);
        assertFalse(root.allowFontFallback());
        assertThrows(FdxException.class, () -> root.textFont(null));
        assertSame(root, root.allowFontFallback(true));
        assertNull(root.textFont(null));
        root.allowFontFallback(false);
        assertThrows(FdxException.class, () -> root.textFont(null));
        root.dispose();
    }

    private static BitmapFont font(int advance) {
        Texture page = proxy(Texture.class, (method, args) -> switch (method) {
            case "width" -> advance * 2;
            case "height" -> 8;
            default -> throw new AssertionError(method);
        });
        return BitmapFont.fromGrid(page, "A?", advance, 8);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> invocation.call(method.getName(), args));
    }

    private interface Invocation { Object call(String method, Object[] args); }

    @Test
    public void reusesKeysWhenFontLookupsAlternate() {
        UiTextEngine engine = new UiTextEngine(null, null);
        UiFont regular = UiFont.freeType("fonts/regular.ttf", 16.0f);
        UiFont title = UiFont.freeType("fonts/title.ttf", 24.0f);

        String regularKey = engine.key(regular, 1.0f);
        String titleKey = engine.key(title, 1.0f);

        assertSame(regularKey, engine.key(regular, 1.0f));
        assertSame(titleKey, engine.key(title, 1.0f));
        assertNotSame(regularKey, engine.key(regular, 1.25f));
    }

    @Test
    public void boundsKeysForShortLivedFontObjects() {
        UiTextEngine engine = new UiTextEngine(null, null);
        UiFont firstFont = UiFont.freeType("fonts/first.ttf", 16.0f);
        String firstKey = engine.key(firstFont, 1.0f);

        for (int i = 0; i < 512; i++) {
            engine.key(UiFont.freeType("fonts/transient-" + i + ".ttf", 16.0f), 1.0f);
        }

        assertNotSame(firstKey, engine.key(firstFont, 1.0f));
    }
}
