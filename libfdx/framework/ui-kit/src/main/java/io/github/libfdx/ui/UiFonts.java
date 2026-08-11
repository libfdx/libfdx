package io.github.libfdx.ui;

/**
 * Provides fonts bundled with UI Kit.
 *
 * @author xpenatan
 */
public final class UiFonts {
    /** The internal/classpath path of the UI Kit default TrueType font. */
    public static final String DEFAULT_TTF_PATH =
            "libfdx-assets/ui/font/LiberationSans-Regular.ttf";

    /** The internal/classpath path of the default font's license. */
    public static final String DEFAULT_FONT_LICENSE_PATH =
            "libfdx-assets/ui/font/OFL.txt";

    private UiFonts() {
    }

    /**
     * Creates a reference to the bundled UI Kit default font.
     *
     * <p>The returned value is an immutable font description. Each UI root
     * rasterizes and owns the corresponding atlas at the effective display
     * scale.</p>
     *
     * @param size the logical font size
     * @return the bundled default font description
     */
    public static UiFont defaultFont(float size) {
        return UiFont.freeType(DEFAULT_TTF_PATH, size);
    }
}
