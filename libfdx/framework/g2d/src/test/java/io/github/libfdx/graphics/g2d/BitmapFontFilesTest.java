package io.github.libfdx.graphics.g2d;

import io.github.libfdx.collections.IntMap;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.files.FileHandle;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.*;
import io.github.libfdx.runtime.core.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class BitmapFontFilesTest {
    private static final String FNT = """
            info face="Test" size=8
            common lineHeight=8 base=8
            page id=0 file="first.png"
            char id=65 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 xadvance=5 page=0
            """;
    private int creations, disposals;
    private final FdxException uploadFailure = new FdxException("atlas upload failed");

    @Test
    void invalidBitmapFileThrowsBeforeAllocatingTextures() {
        assertThrows(FdxException.class, () -> BitmapFontFiles.loadBitmap(graphics(false), files("not a font", false), "bad.fnt"));
        assertEquals(0, creations);
    }

    @Test
    void missingLaterPageReleasesPreviouslyLoadedPages() {
        assertThrows(FdxException.class, () -> BitmapFontFiles.loadBitmap(graphics(false),
                files(FNT + "page id=1 file=\"missing.png\"\n", true), "font.fnt"));
        assertEquals(1, creations);
        assertEquals(1, disposals);
    }

    @Test
    void bitmapUploadFailureReleasesTheNewTextureAndPreservesCause() {
        assertSame(uploadFailure, assertThrows(FdxException.class,
                () -> BitmapFontFiles.loadBitmap(graphics(true), files(FNT, false), "font.fnt")));
        assertEquals(1, creations);
        assertEquals(1, disposals);
    }

    @Test
    void trueTypeUploadFailureReleasesTheNewAtlasAndPreservesCause() {
        RuntimeCoreProvider previous = RuntimeCore.provider();
        FontRasterizer rasterizer = (bytes, options) -> {
            IntMap<RasterizedGlyph> glyphs = new IntMap<>();
            glyphs.put(65, new RasterizedGlyph(65, 0, 0, 1, 1, 0, 0, 5));
            return new RasterizedFont("test", 8, 8, 8, 1, 1, ByteBuffer.allocateDirect(4), glyphs, null);
        };
        RuntimeCore.registerProvider(proxy(RuntimeCoreProvider.class, (method, args) -> rasterizer));
        try {
            assertSame(uploadFailure, assertThrows(FdxException.class, () -> BitmapFontFiles.loadFreeType(
                    graphics(true), files(FNT, false), "font.ttf", FreeTypeFontOptions.defaults(8))));
            assertEquals(1, creations);
            assertEquals(1, disposals);
        } finally { RuntimeCore.registerProvider(previous); }
    }

    private GraphicsContext graphics(boolean failUpload) {
        GraphicsDevice device = proxy(GraphicsDevice.class, (method, args) -> switch (method) {
            case "createTexture" -> {
                creations++;
                yield proxy(Texture.class, (operation, values) -> switch (operation) {
                    case "width", "height" -> 1;
                    case "dispose" -> { disposals++; yield null; }
                    default -> throw new AssertionError(operation);
                });
            }
            case "writeTexture" -> {
                if (failUpload) throw uploadFailure;
                yield null;
            }
            default -> throw new AssertionError(method);
        });
        return proxy(GraphicsContext.class, (method, args) -> method.equals("device") ? device : null);
    }

    private FileSystem files(String text, boolean missingPage) {
        return proxy(FileSystem.class, (method, args) -> {
            String path = (String) args[0];
            return proxy(FileHandle.class, (operation, values) -> {
                if (operation.equals("readString")) return FdxFuture.completed(text);
                if (missingPage && path.endsWith("missing.png")) return FdxFuture.failed(new FdxException("missing page"));
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", bytes);
                return FdxFuture.completed(bytes.toByteArray());
            });
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> invocation.call(method.getName(), args));
    }

    private interface Invocation { Object call(String method, Object[] args) throws Exception; }
}
