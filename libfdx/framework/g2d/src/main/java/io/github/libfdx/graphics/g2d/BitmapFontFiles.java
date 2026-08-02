package io.github.libfdx.graphics.g2d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.IntMap;
import io.github.libfdx.collections.LongMap;
import io.github.libfdx.collections.ObjectMap;
import io.github.libfdx.collections.ObjectIterator;
import io.github.libfdx.assets.loaders.ImageAssetLoader;
import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.runtime.core.FontRasterizerOptions;
import io.github.libfdx.runtime.core.RasterizedFont;
import io.github.libfdx.runtime.core.RasterizedGlyph;
import io.github.libfdx.runtime.core.RuntimeCore;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Represents a bitmap font files.
 *
 * @author xpenatan
 */
public final class BitmapFontFiles {
    private static final String PSP_PROVIDER_ID = "psp";

    private BitmapFontFiles() {
    }

    /**
     * Loads the requested resource.
     *
     * @param graphics the graphics context
     * @param files the files
     * @param path the asset or file path
     * @return the created value
     */
    public static BitmapFont load(GraphicsContext graphics, FileSystem files, String path) {
        String extension = extension(path);
        if ("fnt".equals(extension)) {
            return loadBitmap(graphics, files, path);
        }
        if ("ttf".equals(extension) || "otf".equals(extension)) {
            return loadFreeType(graphics, files, path, FreeTypeFontOptions.defaults(16.0f));
        }
        throw new FdxException("Unsupported font file extension: " + path);
    }

    /**
     * Loads bitmap.
     *
     * @param graphics the graphics context
     * @param files the files
     * @param path the asset or file path
     * @return the created value
     */
    public static BitmapFont loadBitmap(GraphicsContext graphics, FileSystem files, String path) {
        ensure(graphics, files, path);
        String text = files.internal(path).readString(StandardCharsets.UTF_8).get();
        BitmapFontDefinition definition = BitmapFontDefinition.parse(text);
        Array<Texture> pages = new Array<Texture>();
        for (int i = 0; i < definition.pageFiles.size(); i++) {
            String pagePath = resolveSibling(path, definition.pageFiles.get(Integer.valueOf(i)));
            ImageData image = ImageAssetLoader.decode(pagePath, files.internal(pagePath).readBytes().get());
            pages.add(createTexture(graphics, pagePath, image));
        }
        IntMap<BitmapFontGlyph> glyphs = new IntMap<BitmapFontGlyph>();
        ObjectIterator<BitmapFontDefinition.Glyph> glyphIterator = definition.glyphs.values().iterator();
        while (glyphIterator.hasNext()) {
            BitmapFontDefinition.Glyph glyph = glyphIterator.next();
            if (glyph.page >= 0 && glyph.page < pages.size() && glyph.width > 0 && glyph.height > 0) {
                Texture page = pages.get(glyph.page);
                TextureRegion region = new TextureRegion(page, glyph.x, glyph.y, glyph.width, glyph.height);
                glyphs.put(glyph.id, new BitmapFontGlyph(glyph.id, region, glyph.xOffset,
                        glyph.yOffset, glyph.xAdvance));
            }
        }
        return new BitmapFont(definition.face, definition.size, definition.lineHeight, definition.base,
                glyphs, definition.kernings, pages, true);
    }

    /**
     * Loads free type.
     *
     * @param graphics the graphics context
     * @param files the files
     * @param path the asset or file path
     * @param options the options
     * @return the created value
     */
    public static BitmapFont loadFreeType(GraphicsContext graphics, FileSystem files, String path,
            FreeTypeFontOptions options) {
        ensure(graphics, files, path);
        FreeTypeFontOptions actualOptions = options != null ? options : FreeTypeFontOptions.defaults(16.0f);
        RasterizedFont rasterized = RuntimeCore.fontRasterizer().rasterize(files.internal(path).readBytes().get(),
                new FontRasterizerOptions(actualOptions.size(), actualOptions.characters(), actualOptions.padding(),
                        actualOptions.atlasWidth()));
        return createFont(graphics, path, rasterized);
    }

    /**
     * Runs the generate free type step.
     *
     * @param graphics the graphics context
     * @param options the options
     * @return the generate free type
     */
    public static BitmapFont generateFreeType(GraphicsContext graphics, FreeTypeFontOptions options) {
        ensureGraphics(graphics);
        throw unsupportedFamilyFreeType();
    }

    private static Texture createTexture(GraphicsContext graphics, String label, ImageData image) {
        ImageData uploadImage = isPsp(graphics) ? powerOfTwoImage(image) : image;
        Texture texture = graphics.device().createTexture(TextureDescriptor.rgba8(label, uploadImage.width(),
                uploadImage.height()));
        graphics.device().writeTexture(texture, uploadImage.rgba());
        return texture;
    }

    private static ImageData powerOfTwoImage(ImageData image) {
        int width = image.width();
        int height = image.height();
        if (isPowerOfTwo(width) && isPowerOfTwo(height)) {
            return image;
        }
        int paddedWidth = nextPowerOfTwo(width);
        int paddedHeight = nextPowerOfTwo(height);
        int sourceRowBytes = width * 4;
        int targetRowBytes = paddedWidth * 4;
        int targetBytes = targetRowBytes * paddedHeight;
        ByteBuffer source = image.rgba();
        ByteBuffer target = ByteBuffer.allocateDirect(targetBytes);
        for (int i = 0; i < targetBytes; i++) {
            target.put((byte) 0);
        }
        for (int row = 0; row < height; row++) {
            source.position(row * sourceRowBytes);
            target.position(row * targetRowBytes);
            for (int i = 0; i < sourceRowBytes; i++) {
                target.put(source.get());
            }
        }
        target.position(0);
        target.limit(targetBytes);
        return new ImageData(paddedWidth, paddedHeight, target);
    }

    private static boolean isPsp(GraphicsContext graphics) {
        return graphics != null
                && graphics.providerId() != null
                && PSP_PROVIDER_ID.equals(graphics.providerId().value());
    }

    private static boolean isPowerOfTwo(int value) {
        return value > 0 && (value & (value - 1)) == 0;
    }

    private static int nextPowerOfTwo(int value) {
        int power = 1;
        while (power < value) {
            power <<= 1;
        }
        return power;
    }

    private static BitmapFont createFont(GraphicsContext graphics, String label, RasterizedFont rasterized) {
        Texture texture = graphics.device().createTexture(TextureDescriptor.rgba8(label + " atlas",
                rasterized.atlasWidth(), rasterized.atlasHeight()));
        graphics.device().writeTexture(texture, rasterized.rgba());
        Array<Texture> pages = new Array<Texture>();
        pages.add(texture);
        IntMap<BitmapFontGlyph> glyphs = new IntMap<BitmapFontGlyph>();
        ObjectIterator<RasterizedGlyph> glyphIterator = rasterized.glyphs().values().iterator();
        while (glyphIterator.hasNext()) {
            RasterizedGlyph glyph = glyphIterator.next();
            TextureRegion region = new TextureRegion(texture, glyph.x(), glyph.y(), glyph.width(), glyph.height());
            glyphs.put(glyph.codePoint(), new BitmapFontGlyph(glyph.codePoint(), region,
                    glyph.xOffset(), glyph.yOffset(), glyph.xAdvance()));
        }
        return new BitmapFont(rasterized.name(), rasterized.nativeSize(), rasterized.lineHeight(),
                rasterized.baseLine(), glyphs, rasterized.kernings(), pages, true);
    }

    private static void ensure(GraphicsContext graphics, FileSystem files, String path) {
        ensureGraphics(graphics);
        if (files == null) {
            throw new FdxException("FileSystem cannot be null");
        }
        if (path == null || path.trim().length() == 0) {
            throw new FdxException("Font path cannot be empty");
        }
    }

    private static void ensureGraphics(GraphicsContext graphics) {
        if (graphics == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
    }

    private static String extension(String path) {
        int dot = path != null ? path.lastIndexOf('.') : -1;
        return dot >= 0 ? path.substring(dot + 1).toLowerCase() : "";
    }

    private static String resolveSibling(String path, String sibling) {
        if (sibling == null || sibling.length() == 0) {
            return path;
        }
        String normalized = sibling.replace('\\', '/');
        int slash = path != null ? path.replace('\\', '/').lastIndexOf('/') : -1;
        return slash >= 0 ? path.substring(0, slash + 1) + normalized : normalized;
    }

    private static FdxException unsupportedFamilyFreeType() {
        return new FdxException("Portable family font rasterization is not implemented in common g2d. "
                + "Use UiFont.freeType(path, size) with a .ttf/.otf asset or add a backend-specific system font provider.");
    }

    /**
     * Represents a bitmap font definition.
     *
     * @author xpenatan
     */
    private static final class BitmapFontDefinition {
        final IntMap<String> pageFiles = new IntMap<String>();
        final IntMap<Glyph> glyphs = new IntMap<Glyph>();
        final LongMap<Integer> kernings = new LongMap<Integer>();
        String face = "bitmap";
        float size = 16.0f;
        float lineHeight = 16.0f;
        float base = 16.0f;

        static BitmapFontDefinition parse(String text) {
            BitmapFontDefinition definition = new BitmapFontDefinition();
            String value = text != null ? text : "";
            int length = value.length();
            int start = 0;
            while (start <= length) {
                int end = start;
                while (end < length) {
                    char c = value.charAt(end);
                    if (c == '\n' || c == '\r') {
                        break;
                    }
                    end++;
                }
                parseLine(definition, value.substring(start, end).trim());
                if (end >= length) {
                    break;
                }
                char lineBreak = value.charAt(end);
                end++;
                if (lineBreak == '\r' && end < length && value.charAt(end) == '\n') {
                    end++;
                }
                start = end;
            }
            return definition;
        }

        private static void parseLine(BitmapFontDefinition definition, String line) {
            if (line.length() == 0) {
                return;
            }
            String type = type(line);
            ObjectMap<String, String> values = values(line);
            if ("info".equals(type)) {
                definition.face = value(values, "face", definition.face);
                definition.size = floatValue(values, "size", definition.size);
            } else if ("common".equals(type)) {
                definition.lineHeight = floatValue(values, "lineHeight", definition.lineHeight);
                definition.base = floatValue(values, "base", definition.base);
            } else if ("page".equals(type)) {
                definition.pageFiles.put(intValue(values, "id", 0), value(values, "file", ""));
            } else if ("char".equals(type)) {
                Glyph glyph = new Glyph();
                glyph.id = intValue(values, "id", 0);
                glyph.x = intValue(values, "x", 0);
                glyph.y = intValue(values, "y", 0);
                glyph.width = intValue(values, "width", 0);
                glyph.height = intValue(values, "height", 0);
                glyph.xOffset = floatValue(values, "xoffset", 0.0f);
                glyph.yOffset = floatValue(values, "yoffset", 0.0f);
                glyph.xAdvance = floatValue(values, "xadvance", glyph.width);
                glyph.page = intValue(values, "page", 0);
                definition.glyphs.put(glyph.id, glyph);
            } else if ("kerning".equals(type)) {
                int first = intValue(values, "first", 0);
                int second = intValue(values, "second", 0);
                int amount = intValue(values, "amount", 0);
                definition.kernings.put(BitmapFont.kerningKey(first, second), Integer.valueOf(amount));
            }
        }

        private static String type(String line) {
            int space = line.indexOf(' ');
            return space >= 0 ? line.substring(0, space) : line;
        }

        private static ObjectMap<String, String> values(String line) {
            ObjectMap<String, String> result = new ObjectMap<String, String>();
            int index = line.indexOf(' ');
            while (index >= 0 && index < line.length()) {
                while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
                    index++;
                }
                int equals = line.indexOf('=', index);
                if (equals < 0) {
                    break;
                }
                String key = line.substring(index, equals);
                index = equals + 1;
                String value;
                if (index < line.length() && line.charAt(index) == '"') {
                    int end = line.indexOf('"', index + 1);
                    value = end >= 0 ? line.substring(index + 1, end) : line.substring(index + 1);
                    index = end >= 0 ? end + 1 : line.length();
                } else {
                    int end = index;
                    while (end < line.length() && !Character.isWhitespace(line.charAt(end))) {
                        end++;
                    }
                    value = line.substring(index, end);
                    index = end;
                }
                result.put(key, value);
            }
            return result;
        }

        private static String value(ObjectMap<String, String> values, String key, String fallback) {
            String value = values.get(key);
            return value != null ? value : fallback;
        }

        private static int intValue(ObjectMap<String, String> values, String key, int fallback) {
            try {
                return Integer.parseInt(value(values, key, String.valueOf(fallback)));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private static float floatValue(ObjectMap<String, String> values, String key, float fallback) {
            try {
                return Float.parseFloat(value(values, key, String.valueOf(fallback)));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        /**
         * Represents a glyph.
         *
         * @author xpenatan
         */
        static final class Glyph {
            int id;
            int x;
            int y;
            int width;
            int height;
            float xOffset;
            float yOffset;
            float xAdvance;
            int page;
        }
    }

}
