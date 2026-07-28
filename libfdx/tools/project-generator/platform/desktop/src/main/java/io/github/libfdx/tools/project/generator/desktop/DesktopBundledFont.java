package io.github.libfdx.tools.project.generator.desktop;

import io.github.libfdx.Fdx;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.files.DefaultFileSystem;
import io.github.libfdx.tools.project.generator.ui.ProjectGeneratorApplication;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Exposes the generator's bundled TTF through the desktop filesystem.
 *
 * @author xpenatan
 */
final class DesktopBundledFont {
    private static final String FONT_PATH = "font/LiberationSans-Regular.ttf";

    private DesktopBundledFont() {
    }

    /**
     * Extracts the classpath resource to an isolated temporary asset root.
     *
     * @param fdx the desktop runtime
     */
    static void install(Fdx fdx) {
        try {
            InputStream input = ProjectGeneratorApplication.class
                    .getResourceAsStream("/" + FONT_PATH);
            if (input == null) {
                throw new FdxException("Bundled project-generator font is missing: " + FONT_PATH);
            }

            Path root = Files.createTempDirectory("libfdx-project-generator-font-");
            Path font = root.resolve(FONT_PATH);
            Files.createDirectories(font.getParent());
            try {
                Files.copy(input, font, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                input.close();
            }

            root.toFile().deleteOnExit();
            font.getParent().toFile().deleteOnExit();
            font.toFile().deleteOnExit();
            DefaultFileSystem files = fdx.files().as();
            files.addInternalRootFirst(root.toFile());
        } catch (FdxException error) {
            throw error;
        } catch (Exception error) {
            throw new FdxException("Could not prepare the bundled project-generator font", error);
        }
    }
}
