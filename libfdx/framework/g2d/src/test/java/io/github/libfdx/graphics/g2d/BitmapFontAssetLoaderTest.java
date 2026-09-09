package io.github.libfdx.graphics.g2d;

import io.github.libfdx.assets.*;
import io.github.libfdx.collections.IntMap;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.files.FileHandle;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.*;
import io.github.libfdx.runtime.core.*;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class BitmapFontAssetLoaderTest {
    private final RuntimeCoreProvider previous = RuntimeCore.provider();
    private final FdxFuture<byte[]> bytes = FdxFuture.pending();
    private final Thread applicationThread = Thread.currentThread();
    private int reads, rasterizations, uploads, disposals;
    private boolean failRasterization;
    private final FdxException corrupt = new FdxException("invalid TrueType data");
    private DefaultAssetManager manager;

    @AfterEach void cleanup() {
        try { if (manager != null) manager.dispose(); }
        finally { RuntimeCore.registerProvider(previous); }
    }

    @Test
    void deferredReadPrecedesRasterizationAndBudgetedGpuUpload() {
        AssetHandle<BitmapFont> font = load();
        pump();
        assertEquals(1, reads);
        assertEquals(0, rasterizations);
        assertEquals(0, uploads);
        assertFalse(font.future().isDone());
        bytes.complete(new byte[]{1});
        assertEquals(0, rasterizations);
        assertEquals(0, uploads);
        pump();
        assertTrue(font.isLoaded());
        assertEquals(1, rasterizations);
        assertEquals(1, uploads);
        assertTrue(font.asset().hasGlyph('A'));
        manager.unload("font.ttf");
        assertEquals(1, disposals);
    }

    @Test
    void failedReadNeverRasterizesOrUploads() {
        AssetHandle<BitmapFont> font = load();
        FdxException missing = new FdxException("font read failed");
        bytes.completeExceptionally(missing); pump();
        assertSame(missing, assertThrows(FdxException.class, () -> font.future().get()));
        assertEquals(0, rasterizations);
        assertEquals(0, uploads);
    }

    @Test
    void invalidFontDataFailsBeforeCreatingAnAtlas() {
        failRasterization = true;
        AssetHandle<BitmapFont> font = load();
        bytes.complete(new byte[]{1}); pump();
        assertSame(corrupt, assertThrows(FdxException.class, () -> font.future().get()));
        assertEquals(1, rasterizations);
        assertEquals(0, uploads);
    }

    @Test
    void unloadingDuringReadPreventsLateRasterizationAndUpload() {
        AssetHandle<BitmapFont> font = load(); pump();
        manager.unload("font.ttf");
        bytes.complete(new byte[]{1}); pump();
        assertTrue(font.future().isFailed());
        assertEquals(0, rasterizations);
        assertEquals(0, uploads);
    }

    private AssetHandle<BitmapFont> load() {
        FontRasterizer rasterizer = (data, options) -> {
            rasterizations++;
            assertEquals(1, reads);
            if (failRasterization) throw corrupt;
            IntMap<RasterizedGlyph> glyphs = new IntMap<>();
            glyphs.put('A', new RasterizedGlyph('A', 0, 0, 1, 1, 0, 0, 1));
            return new RasterizedFont("test", options.size(), 8, 8, 1, 1,
                    ByteBuffer.allocateDirect(4), glyphs, null);
        };
        RuntimeCore.registerProvider(proxy(RuntimeCoreProvider.class, (method, args) -> rasterizer));
        FileHandle file = proxy(FileHandle.class, (method, args) -> { reads++; return bytes; });
        FileSystem files = proxy(FileSystem.class, (method, args) -> file);
        Texture page = proxy(Texture.class, (method, args) -> switch (method) {
            case "width", "height" -> 1;
            case "dispose" -> { disposals++; yield null; }
            case "isDisposed" -> disposals > 0;
            default -> throw new AssertionError(method);
        });
        GraphicsDevice device = proxy(GraphicsDevice.class, (method, args) -> {
            assertSame(applicationThread, Thread.currentThread());
            if (method.equals("createTexture")) return page;
            if (method.equals("writeTexture")) { uploads++; return null; }
            throw new AssertionError(method);
        });
        GraphicsContext graphics = proxy(GraphicsContext.class, (method, args) -> device);
        manager = new DefaultAssetManager(files);
        G2DAssetLoaders.register(manager, graphics);
        return manager.load(AssetDescriptor.of("font.ttf", BitmapFont.class));
    }

    private void pump() {
        for (int i = 0; i < 40; i++) {
            manager.update(1, Long.MAX_VALUE);
            assertTrue(manager.lastUpdateTaskCount() <= 1);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> invocation.call(method.getName(), args));
    }
    private interface Invocation { Object call(String method, Object[] args); }
}
