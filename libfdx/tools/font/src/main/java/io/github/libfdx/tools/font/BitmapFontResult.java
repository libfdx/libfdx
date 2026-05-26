package io.github.libfdx.tools.font;

import java.nio.file.Path;
import java.util.List;

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

    public Path assetRoot() {
        return assetRoot;
    }

    public Path fontFile() {
        return fontFile;
    }

    public Path imageFile() {
        return imageFile;
    }

    public String assetFontPath() {
        return assetFontPath;
    }

    public String assetImagePath() {
        return assetImagePath;
    }

    public List<Path> files() {
        return List.of(fontFile, imageFile);
    }
}
