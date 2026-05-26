package io.github.libfdx.tools.font;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BitmapFontGenerator {
    private BitmapFontGenerator() {
    }

    public static BitmapFontResult generate(BitmapFontSpec spec) {
        Path source = spec.requireSourceFile();
        Path assetRoot = spec.requireOutputDirectory();
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("Bitmap font source TTF was not found: " + source);
        }
        String assetPath = spec.assetPath();
        Path assetDirectory = ".".equals(assetPath) ? assetRoot : assetRoot.resolve(assetPath);
        Path fontFile = assetDirectory.resolve(spec.name() + ".fnt");
        Path imageFile = assetDirectory.resolve(spec.name() + ".png");
        try {
            Files.createDirectories(assetDirectory);
            Font font = loadFont(source, spec.size());
            FontRenderContext context = new FontRenderContext(null, true, true);
            LineMetrics lineMetrics = font.getLineMetrics(spec.characters(), context);
            int lineHeight = Math.max(1, (int) Math.ceil(lineMetrics.getHeight()) + spec.padding() * 2);
            int baseLine = Math.max(1, (int) Math.ceil(lineMetrics.getAscent()) + spec.padding());
            List<GlyphCell> glyphs = measureGlyphs(spec, font, context, lineHeight);
            AtlasLayout layout = packGlyphs(glyphs, spec.maxTextureSize(), lineHeight);
            BufferedImage atlas = new BufferedImage(layout.width, layout.height, BufferedImage.TYPE_INT_ARGB);
            drawGlyphs(atlas, glyphs, font, baseLine, spec.padding());
            if (!ImageIO.write(atlas, "png", imageFile.toFile())) {
                throw new IllegalStateException("No PNG writer is available for bitmap font atlas output.");
            }
            writeFontFile(spec, fontFile, imageFile.getFileName().toString(), font.getFontName(Locale.ROOT),
                    layout.width, layout.height, lineHeight, baseLine, glyphs);
            String assetFontPath = ".".equals(assetPath) ? fontFile.getFileName().toString()
                    : assetPath + "/" + fontFile.getFileName();
            String assetImagePath = ".".equals(assetPath) ? imageFile.getFileName().toString()
                    : assetPath + "/" + imageFile.getFileName();
            return new BitmapFontResult(assetRoot, fontFile, imageFile, assetFontPath, assetImagePath);
        } catch (IOException error) {
            throw new IllegalStateException("Could not generate bitmap font " + spec.name() + " from " + source,
                    error);
        }
    }

    private static Font loadFont(Path source, int size) throws IOException {
        try (var input = Files.newInputStream(source)) {
            return Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(Font.PLAIN, (float) size);
        } catch (FontFormatException error) {
            throw new IllegalArgumentException("Bitmap font source is not a readable TTF: " + source, error);
        }
    }

    private static List<GlyphCell> measureGlyphs(BitmapFontSpec spec, Font font, FontRenderContext context,
            int lineHeight) {
        ArrayList<GlyphCell> glyphs = new ArrayList<>();
        spec.characters().codePoints().forEach(codePoint -> {
            String text = new String(Character.toChars(codePoint));
            int advance = Math.max(1, (int) Math.ceil(font.getStringBounds(text, context).getWidth()));
            int width = Math.max(1, advance + spec.padding() * 2);
            glyphs.add(new GlyphCell(codePoint, width, lineHeight, advance));
        });
        return glyphs;
    }

    private static AtlasLayout packGlyphs(List<GlyphCell> glyphs, int maxTextureSize, int lineHeight) {
        int x = 0;
        int y = 0;
        int rowHeight = lineHeight;
        int usedWidth = 0;
        int usedHeight = rowHeight;
        for (GlyphCell glyph : glyphs) {
            if (glyph.width > maxTextureSize) {
                throw new IllegalArgumentException("Bitmap font glyph " + glyph.codePoint
                        + " is wider than maxTextureSize " + maxTextureSize);
            }
            if (x > 0 && x + glyph.width > maxTextureSize) {
                x = 0;
                y += rowHeight;
                usedHeight = y + rowHeight;
            }
            if (usedHeight > maxTextureSize) {
                throw new IllegalArgumentException("Bitmap font atlas is taller than maxTextureSize "
                        + maxTextureSize);
            }
            glyph.x = x;
            glyph.y = y;
            x += glyph.width;
            usedWidth = Math.max(usedWidth, x);
        }
        int width = nextPowerOfTwo(Math.max(64, usedWidth));
        int height = nextPowerOfTwo(Math.max(64, usedHeight));
        if (width > maxTextureSize || height > maxTextureSize) {
            throw new IllegalArgumentException("Bitmap font atlas " + width + "x" + height
                    + " exceeds maxTextureSize " + maxTextureSize);
        }
        return new AtlasLayout(width, height);
    }

    private static void drawGlyphs(BufferedImage atlas, List<GlyphCell> glyphs, Font font, int baseLine, int padding) {
        Graphics2D graphics = atlas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setFont(font);
            graphics.setColor(Color.WHITE);
            FontMetrics metrics = graphics.getFontMetrics();
            for (GlyphCell glyph : glyphs) {
                if (glyph.codePoint == 32) {
                    continue;
                }
                String text = new String(Character.toChars(glyph.codePoint));
                int drawX = glyph.x + padding;
                int drawY = glyph.y + baseLine + metrics.getLeading() / 2;
                graphics.drawString(text, drawX, drawY);
            }
        } finally {
            graphics.dispose();
        }
    }

    private static void writeFontFile(BitmapFontSpec spec, Path fontFile, String imageName, String face, int width,
            int height, int lineHeight, int baseLine, List<GlyphCell> glyphs) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(fontFile, StandardCharsets.UTF_8)) {
            writer.write("info face=\"" + bmFontText(face) + "\" size=" + spec.size()
                    + " bold=0 italic=0 charset=\"\" unicode=1 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=0,0");
            writer.newLine();
            writer.write("common lineHeight=" + lineHeight + " base=" + baseLine + " scaleW=" + width
                    + " scaleH=" + height + " pages=1 packed=0");
            writer.newLine();
            writer.write("page id=0 file=\"" + bmFontText(imageName) + "\"");
            writer.newLine();
            writer.write("chars count=" + glyphs.size());
            writer.newLine();
            for (GlyphCell glyph : glyphs) {
                writer.write("char id=" + glyph.codePoint
                        + " x=" + glyph.x
                        + " y=" + glyph.y
                        + " width=" + glyph.width
                        + " height=" + glyph.height
                        + " xoffset=0 yoffset=0"
                        + " xadvance=" + glyph.advance
                        + " page=0 chnl=15");
                writer.newLine();
            }
            writer.write("kernings count=0");
            writer.newLine();
        }
    }

    private static int nextPowerOfTwo(int value) {
        int current = 1;
        while (current < value) {
            current <<= 1;
        }
        return current;
    }

    private static String bmFontText(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class GlyphCell {
        private final int codePoint;
        private final int width;
        private final int height;
        private final int advance;
        private int x;
        private int y;

        private GlyphCell(int codePoint, int width, int height, int advance) {
            this.codePoint = codePoint;
            this.width = width;
            this.height = height;
            this.advance = advance;
        }
    }

    private record AtlasLayout(int width, int height) {
    }
}
