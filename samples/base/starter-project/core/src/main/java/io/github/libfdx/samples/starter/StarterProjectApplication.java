package io.github.libfdx.samples.starter;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.assets.loaders.ImageAssetLoader;
import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.display.Display;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.g2d.Batch2D;
import io.github.libfdx.graphics.g2d.SpriteBatch;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Clears the main display and draws the bundled libFDX logo.
 *
 * <p>This class contains the complete portable application. Platform modules
 * only choose a backend and graphics provider before starting it.</p>
 *
 * @author xpenatan
 */
public final class StarterProjectApplication extends ApplicationAdapter {
    private static final String LOGO_ASSET = "fdx_logo_dark.png";
    private static final float MAX_LOGO_WIDTH = 1.35f;
    private static final float MAX_LOGO_HEIGHT = 1.00f;

    private final AssetSource assetSource;
    private final long exitAfterFrames;
    private Application application;
    private Display display;
    private Batch2D batch;
    private Texture logo;
    private long renderedFrames;

    /**
     * Creates an application that runs until the platform requests an exit.
     */
    public StarterProjectApplication() {
        this(new InternalAssetSource(), 0L);
    }

    /**
     * Creates an application with an optional bounded lifetime for smoke tests.
     *
     * @param exitAfterFrames frames to render before exiting, or zero to keep running
     */
    public StarterProjectApplication(long exitAfterFrames) {
        this(new InternalAssetSource(), exitAfterFrames);
    }

    /**
     * Creates an application that reads assets from a native working directory.
     *
     * <p>TeaVM C projects copy assets to the native filesystem rather than a
     * Java classpath. Desktop C passes {@code "assets"}; iOS C starts inside
     * the bundled assets directory and passes an empty root.</p>
     *
     * @param assetRoot path from the native working directory to the assets
     * @return the configured application
     */
    public static StarterProjectApplication nativeAssets(String assetRoot) {
        return nativeAssets(assetRoot, 0L);
    }

    /**
     * Creates an application that reads assets from a native working directory
     * and optionally exits after a fixed number of frames.
     *
     * @param assetRoot path from the native working directory to the assets
     * @param exitAfterFrames frames to render before exiting, or zero to keep running
     * @return the configured application
     */
    public static StarterProjectApplication nativeAssets(String assetRoot, long exitAfterFrames) {
        return new StarterProjectApplication(new LocalAssetSource(assetRoot), exitAfterFrames);
    }

    private StarterProjectApplication(AssetSource assetSource, long exitAfterFrames) {
        this.assetSource = assetSource;
        this.exitAfterFrames = exitAfterFrames;
    }

    /**
     * Loads the logo and creates portable rendering resources.
     *
     * @param fdx the backend-owned libFDX runtime root
     */
    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        display = fdx.displays().main();
        GraphicsContext graphics = fdx.graphics().main();

        ImageData image = ImageAssetLoader.decode(
                LOGO_ASSET, assetSource.read(fdx.files(), LOGO_ASSET));
        logo = graphics.device().createTexture(
                TextureDescriptor.rgba8(LOGO_ASSET, image.width(), image.height()));
        graphics.device().writeTexture(logo, image.rgba());
        batch = new SpriteBatch(graphics);
    }

    /**
     * Clears the screen and draws the logo centered at its original aspect ratio.
     */
    @Override
    public void render() {
        float height = logoHeight();
        float width = logoWidth(height);

        batch.begin(LoadOp.clear(0.035f, 0.047f, 0.075f, 1.0f));
        batch.draw(logo, -width * 0.5f, -height * 0.5f, width, height);
        batch.end();

        renderedFrames++;
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    /**
     * Releases the resources owned by the portable application.
     */
    @Override
    public void dispose() {
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        if (logo != null) {
            logo.dispose();
            logo = null;
        }
    }

    private float logoWidth(float height) {
        float width = height * textureAspect() / displayAspect();
        return Math.min(width, MAX_LOGO_WIDTH);
    }

    private float logoHeight() {
        float widthAtMaximumHeight =
                MAX_LOGO_HEIGHT * textureAspect() / displayAspect();
        if (widthAtMaximumHeight <= MAX_LOGO_WIDTH) {
            return MAX_LOGO_HEIGHT;
        }
        return MAX_LOGO_WIDTH * displayAspect() / textureAspect();
    }

    private float textureAspect() {
        return logo.width() / (float) logo.height();
    }

    private float displayAspect() {
        int width = display.framebufferWidth() > 0
                ? display.framebufferWidth()
                : display.width();
        int height = display.framebufferHeight() > 0
                ? display.framebufferHeight()
                : display.height();
        if (width <= 0 || height <= 0) {
            return 4.0f / 3.0f;
        }
        return width / (float) height;
    }

    private interface AssetSource {
        byte[] read(FileSystem files, String path);
    }

    private static final class InternalAssetSource implements AssetSource {
        @Override
        public byte[] read(FileSystem files, String path) {
            return files.internal(path).readBytes().get();
        }
    }

    private static final class LocalAssetSource implements AssetSource {
        private final String root;

        private LocalAssetSource(String root) {
            String normalized = root != null ? root.replace('\\', '/') : "";
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            this.root = normalized;
        }

        @Override
        public byte[] read(FileSystem files, String path) {
            String assetPath = root.length() == 0 ? path : root + "/" + path;
            try {
                FileInputStream input = new FileInputStream(assetPath);
                try {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[16 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                    return output.toByteArray();
                } finally {
                    input.close();
                }
            } catch (IOException error) {
                throw new FdxException("Could not read native asset: " + assetPath, error);
            }
        }
    }
}
