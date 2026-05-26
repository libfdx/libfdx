package io.github.libfdx.tools.font;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class BitmapFontSpec {
    public static final String DEFAULT_ASSET_PATH = "font/bitmap";

    private final Path sourceFile;
    private final Path outputDirectory;
    private final String name;
    private final String assetPath;
    private final int size;
    private final int padding;
    private final int maxTextureSize;
    private final String characters;

    private BitmapFontSpec(Builder builder) {
        this.sourceFile = builder.sourceFile;
        this.outputDirectory = builder.outputDirectory;
        this.name = requireName(builder.name);
        this.assetPath = normalizeAssetPath(builder.assetPath);
        this.size = builder.size > 0 ? builder.size : 24;
        this.padding = Math.max(0, builder.padding);
        this.maxTextureSize = Math.max(64, builder.maxTextureSize);
        this.characters = builder.characters != null && !builder.characters.isEmpty()
                ? unique(builder.characters)
                : defaultCharacters();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Path sourceFile() {
        return sourceFile;
    }

    public Path outputDirectory() {
        return outputDirectory;
    }

    public String name() {
        return name;
    }

    public String assetPath() {
        return assetPath;
    }

    public int size() {
        return size;
    }

    public int padding() {
        return padding;
    }

    public int maxTextureSize() {
        return maxTextureSize;
    }

    public String characters() {
        return characters;
    }

    public BitmapFontSpec withOutputDirectory(Path outputDirectory) {
        return builder()
                .sourceFile(sourceFile)
                .outputDirectory(outputDirectory)
                .name(name)
                .assetPath(assetPath)
                .size(size)
                .padding(padding)
                .maxTextureSize(maxTextureSize)
                .characters(characters)
                .build();
    }

    Path requireSourceFile() {
        if (sourceFile == null) {
            throw new IllegalArgumentException("Bitmap font sourceFile must be set for " + name);
        }
        return sourceFile.toAbsolutePath().normalize();
    }

    Path requireOutputDirectory() {
        if (outputDirectory == null) {
            throw new IllegalArgumentException("Bitmap font outputDirectory must be set for " + name);
        }
        return outputDirectory.toAbsolutePath().normalize();
    }

    static String normalizeAssetPath(String value) {
        String path = value == null || value.isBlank() ? DEFAULT_ASSET_PATH : value.trim().replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        Path parsed = Path.of(path);
        if (parsed.isAbsolute()) {
            throw new IllegalArgumentException("Bitmap font assetPath must be relative: " + value);
        }
        for (Path part : parsed) {
            String text = part.toString();
            if ("..".equals(text)) {
                throw new IllegalArgumentException("Bitmap font assetPath cannot contain '..': " + value);
            }
        }
        return path.isEmpty() ? "." : path;
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Bitmap font name must be set");
        }
        String name = value.trim();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') {
                throw new IllegalArgumentException(
                        "Bitmap font name may only contain letters, digits, '_' and '-': " + value);
            }
        }
        return name;
    }

    private static String defaultCharacters() {
        StringBuilder builder = new StringBuilder();
        for (char c = 32; c <= 126; c++) {
            builder.append(c);
        }
        return builder.toString();
    }

    private static String unique(String characters) {
        Set<Integer> seen = new LinkedHashSet<>();
        characters.codePoints().forEach(seen::add);
        StringBuilder builder = new StringBuilder();
        for (Integer codePoint : seen) {
            builder.appendCodePoint(codePoint.intValue());
        }
        return builder.toString();
    }

    public static final class Builder {
        private Path sourceFile;
        private Path outputDirectory;
        private String name;
        private String assetPath = DEFAULT_ASSET_PATH;
        private int size = 24;
        private int padding = 2;
        private int maxTextureSize = 512;
        private String characters;

        private Builder() {
        }

        public Builder sourceFile(Path sourceFile) {
            this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
            return this;
        }

        public Builder outputDirectory(Path outputDirectory) {
            this.outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
            return this;
        }

        public Builder name(String name) {
            this.name = Objects.requireNonNull(name, "name");
            return this;
        }

        public Builder assetPath(String assetPath) {
            this.assetPath = Objects.requireNonNull(assetPath, "assetPath");
            return this;
        }

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public Builder padding(int padding) {
            this.padding = padding;
            return this;
        }

        public Builder maxTextureSize(int maxTextureSize) {
            this.maxTextureSize = maxTextureSize;
            return this;
        }

        public Builder characters(String characters) {
            this.characters = Objects.requireNonNull(characters, "characters");
            return this;
        }

        public BitmapFontSpec build() {
            return new BitmapFontSpec(this);
        }
    }
}
