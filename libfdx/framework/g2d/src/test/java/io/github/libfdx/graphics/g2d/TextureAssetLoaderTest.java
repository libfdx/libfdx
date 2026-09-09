package io.github.libfdx.graphics.g2d;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetHandle;
import io.github.libfdx.assets.AssetLease;
import io.github.libfdx.assets.AssetScope;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.files.FileHandle;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.Texture;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

final class TextureAssetLoaderTest {
    private final Thread applicationThread = Thread.currentThread();
    private final FdxFuture<byte[]> read = FdxFuture.pending();
    private int reads;
    private int uploads;
    private int disposals;
    private boolean failUpload;
    private io.github.libfdx.graphics.TextureDescriptor createdDescriptor;

    @Test
    void immutableSamplingOptionsReachRegionDependencyAndRejectConflictingCacheEntries() throws Exception {
        DefaultAssetManager manager = manager();
        try {
            AssetHandle<TextureRegion> region = manager.load(TextureLoadOptions.PIXEL_ART.descriptor("pixel.png", TextureRegion.class));
            manager.update(); read.complete(png()); drain(manager);
            assertTrue(region.isLoaded());
            assertEquals(io.github.libfdx.graphics.TextureFilter.NEAREST, createdDescriptor.filter());
            assertEquals(io.github.libfdx.graphics.TextureFormat.RGBA8_UNORM, createdDescriptor.format());
            assertThrows(FdxException.class, () -> manager.load(AssetDescriptor.of("pixel.png", Texture.class)));
            assertEquals(1, uploads);
        } finally { manager.dispose(); }
    }
    private final Texture texture = proxy(Texture.class, (method, args) -> switch (method) {
        case "width", "height" -> 1;
        case "dispose" -> { disposals++; yield null; }
        case "isDisposed" -> disposals > 0;
        default -> throw new UnsupportedOperationException(method);
    });

    @Test
    void scopedRegionsKeepTheSharedGpuTextureUntilBothLevelsClose() throws Exception {
        DefaultAssetManager manager = manager();
        try {
            AssetScope first = manager.createScope();
            AssetScope second = manager.createScope();
            AssetLease<TextureRegion> a = first.load(AssetDescriptor.of("pixel.png", TextureRegion.class));
            AssetLease<TextureRegion> b = second.load(AssetDescriptor.of("pixel.png", TextureRegion.class));
            manager.update();
            read.complete(png());
            drain(manager);
            assertSame(a.asset(), b.asset());
            assertEquals(1, reads);
            assertEquals(1, uploads);
            first.dispose();
            manager.unload("pixel.png");
            assertNull(a.asset());
            assertSame(texture, b.asset().texture());
            assertEquals(0, disposals);
            assertNotNull(manager.find("pixel.png", ImageData.class));
            second.dispose();
            assertEquals(1, disposals);
            assertNull(manager.find("pixel.png", ImageData.class));
            assertNull(manager.find("pixel.png", Texture.class));
        } finally {
            manager.dispose();
        }
        assertEquals(1, disposals);
    }

    @Test
    void pendingImageLoadsBeforeSharedTextureAndRegionAndRetainsThem() throws Exception {
        DefaultAssetManager manager = manager();
        try {
            AssetHandle<TextureRegion> region = manager.load(AssetDescriptor.of("pixel.png", TextureRegion.class));
            AssetHandle<Texture> directTexture = manager.load(AssetDescriptor.of("pixel.png", Texture.class));
            assertFalse(manager.update());
            assertEquals(1, reads);
            assertEquals(0, uploads);
            assertFalse(region.future().isDone());
            read.complete(png());
            assertEquals(0, uploads);
            drain(manager);
            assertEquals(1, uploads);
            assertSame(texture, directTexture.asset());
            assertSame(texture, region.asset().texture());
            assertNotNull(manager.find("pixel.png", ImageData.class));
            manager.unload("pixel.png");
            assertEquals(1, disposals);
            assertNull(manager.find("pixel.png", ImageData.class));
        } finally {
            manager.dispose();
        }
        assertEquals(1, disposals);
    }

    @Test
    void failedUploadDisposesTextureAndFailsTheRegion() throws Exception {
        failUpload = true;
        DefaultAssetManager manager = manager();
        try {
            AssetHandle<TextureRegion> region = manager.load(AssetDescriptor.of("pixel.png", TextureRegion.class));
            manager.update();
            read.complete(png());
            drain(manager);
            assertTrue(region.future().isFailed());
            assertEquals(1, uploads);
            assertEquals(1, disposals);
            assertNull(manager.find("pixel.png", ImageData.class));
        } finally {
            manager.dispose();
        }
        assertEquals(1, disposals);
    }

    private DefaultAssetManager manager() {
        FileHandle file = proxy(FileHandle.class, (method, args) -> {
            if (method.equals("readBytes")) { reads++; return read; }
            throw new UnsupportedOperationException(method);
        });
        FileSystem files = proxy(FileSystem.class, (method, args) -> file);
        GraphicsDevice device = proxy(GraphicsDevice.class, (method, args) -> {
            assertSame(applicationThread, Thread.currentThread());
            if (method.equals("createTexture")) { createdDescriptor = (io.github.libfdx.graphics.TextureDescriptor) args[0]; return texture; }
            if (method.equals("writeTexture")) {
                uploads++;
                if (failUpload) { throw new FdxException("upload failed"); }
                return null;
            }
            throw new UnsupportedOperationException(method);
        });
        GraphicsContext graphics = proxy(GraphicsContext.class, (method, args) -> device);
        DefaultAssetManager manager = new DefaultAssetManager(files);
        G2DAssetLoaders.register(manager, graphics);
        return manager;
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", bytes);
        return bytes.toByteArray();
    }

    private static void drain(DefaultAssetManager manager) {
        for (int i = 0; i < 100; i++) {
            if (manager.update(1, Long.MAX_VALUE)) { return; }
            assertEquals(1, manager.lastUpdateTaskCount());
        }
        fail("Texture dependency graph did not settle");
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> invocation.call(method.getName(), args));
    }

    private interface Invocation {
        Object call(String method, Object[] args);
    }
}
