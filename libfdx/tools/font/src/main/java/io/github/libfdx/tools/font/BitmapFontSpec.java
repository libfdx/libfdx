package io.github.libfdx.tools.font;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a bitmap font spec.
 *
 * @author xpenatan
 */
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

    /**
     * Returns the builder.
     *
     * @return the created value
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the source file.
     *
     * @return the source file
     */
    public Path sourceFile() {
        return sourceFile;
    }

    /**
     * Returns the output directory.
     *
     * @return the output directory
     */
    public Path outputDirectory() {
        return outputDirectory;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the asset path.
     *
     * @return the asset path
     */
    public String assetPath() {
        return assetPath;
    }

    /**
     * Returns the size.
     *
     * @return the size
     */
    public int size() {
        return size;
    }

    /**
     * Returns the padding.
     *
     * @return the padding
     */
    public int padding() {
        return padding;
    }

    /**
     * Returns the max texture size.
     *
     * @return the max texture size
     */
    public int maxTextureSize() {
        return maxTextureSize;
    }

    /**
     * Returns the characters.
     *
     * @return the characters
     */
    public String characters() {
        return characters;
    }

    /**
     * Sets the with output directory and returns this bitmap font spec.
     *
     * @param outputDirectory the output directory
     * @return this bitmap font spec for chaining
     */
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

    /**
     * Builds value instances and related output.
     *
     * @author xpenatan
     */
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

        /**
         * Sets the source file and returns this builder.
         *
         * @param sourceFile the source file
         * @return this builder for chaining
         */
        public Builder sourceFile(Path sourceFile) {
            this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
            return this;
        }

        /**
         * Sets the output directory and returns this builder.
         *
         * @param outputDirectory the output directory
         * @return this builder for chaining
         */
        public Builder outputDirectory(Path outputDirectory) {
            this.outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
            return this;
        }

        /**
         * Sets the name and returns this builder.
         *
         * @param name the name
         * @return this builder for chaining
         */
        public Builder name(String name) {
            this.name = Objects.requireNonNull(name, "name");
            return this;
        }

        /**
         * Sets the asset path and returns this builder.
         *
         * @param assetPath the asset path
         * @return this builder for chaining
         */
        public Builder assetPath(String assetPath) {
            this.assetPath = Objects.requireNonNull(assetPath, "assetPath");
            return this;
        }

        /**
         * Sets the size and returns this builder.
         *
         * @param size the size
         * @return this builder for chaining
         */
        public Builder size(int size) {
            this.size = size;
            return this;
        }

        /**
         * Sets the padding and returns this builder.
         *
         * @param padding the padding
         * @return this builder for chaining
         */
        public Builder padding(int padding) {
            this.padding = padding;
            return this;
        }

        /**
         * Sets the max texture size and returns this builder.
         *
         * @param maxTextureSize the max texture size
         * @return this builder for chaining
         */
        public Builder maxTextureSize(int maxTextureSize) {
            this.maxTextureSize = maxTextureSize;
            return this;
        }

        /**
         * Sets the characters and returns this builder.
         *
         * @param characters the characters
         * @return this builder for chaining
         */
        public Builder characters(String characters) {
            this.characters = Objects.requireNonNull(characters, "characters");
            return this;
        }

        /**
         * Returns the build.
         *
         * @return the created value
         */
        public BitmapFontSpec build() {
            return new BitmapFontSpec(this);
        }
    }
}
