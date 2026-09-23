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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
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
    private static final String BITMAP_PATH = "fonts/test.fnt";
    private static final String BITMAP = "info face=Test size=8\ncommon lineHeight=8 base=8\n"
            + "page id=0 file=first.png\npage id=1 file=second.png\n"
            + "char id=65 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 xadvance=5 page=0\n"
            + "char id=66 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 xadvance=5 page=1\n";
    private final Map<String, FdxFuture<byte[]>> bitmapReads = new HashMap<>();
    private final Map<String, Integer> bitmapReadCounts = new HashMap<>();
    private int textureCreations;
    private int failUploadAt;

    @Test
    void deferredBitmapDefinitionAndPagesLoadAcrossUpdatesAndShareOwnership() throws Exception {
        AssetLease<BitmapFont> first = loadBitmap();
        AssetLease<BitmapFont> second = manager.acquire(AssetDescriptor.of(BITMAP_PATH, BitmapFont.class));
        pump();
        assertEquals(Map.of(BITMAP_PATH, 1), bitmapReadCounts);
        assertFalse(first.future().isDone());
        completeDefinition();
        assertEquals(0, textureCreations);
        pump();
        assertEquals(Map.of(BITMAP_PATH, 1, "fonts/first.png", 1, "fonts/second.png", 1), bitmapReadCounts);
        bitmapReads.get("fonts/second.png").complete(png());
        pump();
        assertFalse(first.future().isDone());
        assertEquals(0, textureCreations);
        bitmapReads.get("fonts/first.png").complete(png());
        assertEquals(0, uploads);
        pump();
        assertTrue(first.isLoaded());
        assertSame(first.asset(), second.asset());
        assertTrue(first.asset().hasGlyph('A'));
        assertTrue(first.asset().hasGlyph('B'));
        assertEquals(2, uploads);
        first.dispose();
        assertEquals(0, disposals);
        assertTrue(second.isLoaded());
        second.dispose();
        assertEquals(2, disposals);
        assertNull(manager.find("fonts/first.png", io.github.libfdx.assets.loaders.ImageData.class));
        assertNull(manager.find("fonts/second.png", io.github.libfdx.assets.loaders.ImageData.class));
    }

    @Test
    void deferredBitmapPageFailureFailsFontWithoutGpuAllocation() throws Exception {
        AssetLease<BitmapFont> font = loadBitmap();
        completeDefinition(); pump();
        bitmapReads.get("fonts/first.png").complete(png()); pump();
        FdxException missing = new FdxException("missing second page");
        bitmapReads.get("fonts/second.png").completeExceptionally(missing); pump();
        assertTrue(font.future().isFailed());
        assertThrows(FdxException.class, font.future()::get);
        assertEquals(0, textureCreations);
        assertNull(manager.find("fonts/first.png", io.github.libfdx.assets.loaders.ImageData.class));
    }

    @Test
    void releasingFontDuringDefinitionReadPreventsLatePageRequests() {
        AssetLease<BitmapFont> font = loadBitmap(); pump();
        font.dispose();
        completeDefinition(); pump();
        assertTrue(font.future().isFailed());
        assertEquals(Map.of(BITMAP_PATH, 1), bitmapReadCounts);
        assertEquals(0, textureCreations);
    }

    @Test
    void disposingManagerDuringPageReadsPreventsLateUploads() throws Exception {
        AssetLease<BitmapFont> font = loadBitmap();
        completeDefinition(); pump();
        manager.dispose();
        bitmapReads.get("fonts/first.png").complete(png());
        bitmapReads.get("fonts/second.png").complete(png());
        assertTrue(font.future().isFailed());
        assertEquals(0, textureCreations);
    }

    @Test
    void laterBitmapUploadFailureReleasesEveryCreatedPage() throws Exception {
        AssetLease<BitmapFont> font = loadBitmap();
        failUploadAt = 2;
        completeDefinition(); pump();
        bitmapReads.get("fonts/first.png").complete(png());
        bitmapReads.get("fonts/second.png").complete(png()); pump();
        assertTrue(font.future().isFailed());
        assertEquals(2, textureCreations);
        assertEquals(2, disposals);
        manager.dispose();
        assertEquals(2, disposals);
    }

    @Test
    void invalidBitmapDefinitionFailsBeforeRequestingPages() {
        AssetLease<BitmapFont> font = loadBitmap();
        bitmapReads.get(BITMAP_PATH).complete("not a font".getBytes(StandardCharsets.UTF_8)); pump();
        assertTrue(font.future().isFailed());
        assertEquals(Map.of(BITMAP_PATH, 1), bitmapReadCounts);
        assertEquals(0, textureCreations);
    }

    private void completeDefinition() {
        bitmapReads.get(BITMAP_PATH).complete(BITMAP.getBytes(StandardCharsets.UTF_8));
    }

    private AssetLease<BitmapFont> loadBitmap() {
        for (String path : new String[]{BITMAP_PATH, "fonts/first.png", "fonts/second.png"}) {
            bitmapReads.put(path, FdxFuture.pending());
        }
        FileSystem files = proxy(FileSystem.class, (method, args) -> {
            String path = (String)args[0];
            return proxy(FileHandle.class, (operation, values) -> {
                assertEquals("readBytes", operation, "Managed fonts must compose byte futures");
                bitmapReadCounts.merge(path, 1, Integer::sum);
                return bitmapReads.get(path);
            });
        });
        GraphicsDevice device = proxy(GraphicsDevice.class, (method, args) -> {
            assertSame(applicationThread, Thread.currentThread());
            if (method.equals("createTexture")) {
                textureCreations++;
                boolean[] disposed = {false};
                return proxy(Texture.class, (operation, values) -> switch (operation) {
                    case "width", "height" -> 1;
                    case "dispose" -> { if (!disposed[0]) { disposed[0] = true; disposals++; } yield null; }
                    case "isDisposed" -> disposed[0];
                    default -> throw new AssertionError(operation);
                });
            }
            if (method.equals("writeTexture")) {
                if (++uploads == failUploadAt) throw new FdxException("page upload failed");
                return null;
            }
            throw new AssertionError(method);
        });
        GraphicsContext graphics = proxy(GraphicsContext.class, (method, args) -> method.equals("device") ? device : null);
        manager = new DefaultAssetManager(files);
        G2DAssetLoaders.register(manager, graphics);
        return manager.acquire(AssetDescriptor.of(BITMAP_PATH, BitmapFont.class));
    }

    private static byte[] png() throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1,
                java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", bytes);
        return bytes.toByteArray();
    }

    @AfterEach
    void cleanup() {
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
