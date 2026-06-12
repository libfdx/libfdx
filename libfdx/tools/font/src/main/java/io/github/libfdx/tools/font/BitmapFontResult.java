package io.github.libfdx.tools.font;

import java.nio.file.Path;
import java.util.List;

/**
 * Represents the result of a bitmap font operation.
 *
 * @author xpenatan
 */
public final class BitmapFontResult {
    private final Path assetRoot;
    private final Path fontFile;
    private final Path imageFile;
    private final String assetFontPath;
    private final String assetImagePath;

    BitmapFontResult(Path assetRoot, Path fontFile, Path imageFile, String assetFontPath, String assetImagePath) {
        this.assetRoot = assetRoot;
        this.fontFile = fontFile;
        this.imageFile = imageFile;
        this.assetFontPath = assetFontPath;
        this.assetImagePath = assetImagePath;
    }

    /**
     * Returns the asset root.
     *
     * @return the asset root
     */
    public Path assetRoot() {
        return assetRoot;
    }

    /**
     * Returns the font file.
     *
     * @return the font file
     */
    public Path fontFile() {
        return fontFile;
    }

    /**
     * Returns the image file.
     *
     * @return the image file
     */
    public Path imageFile() {
        return imageFile;
    }

    /**
     * Returns the asset font path.
     *
     * @return the asset font path
     */
    public String assetFontPath() {
        return assetFontPath;
    }

    /**
     * Returns the asset image path.
     *
     * @return the asset image path
     */
    public String assetImagePath() {
        return assetImagePath;
    }

    /**
     * Returns the files.
     *
     * @return the files
     */
    public List<Path> files() {
        return List.of(fontFile, imageFile);
    }
}
