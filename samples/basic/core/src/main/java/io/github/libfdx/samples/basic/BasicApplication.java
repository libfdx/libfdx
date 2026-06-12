package io.github.libfdx.samples.basic;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.Logger;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.g2d.ShapeRenderer2D;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiToolkit;

/**
 * Represents a basic application.
 *
 * @author xpenatan
 */
public final class BasicApplication extends ApplicationAdapter {
    private static final float BACKGROUND_RED = 1.0f;
    private static final float BACKGROUND_GREEN = 1.0f;
    private static final float BACKGROUND_BLUE = 1.0f;
    private static final float BACKGROUND_ALPHA = 1.0f;

    private final long exitAfterFrames;
    private Application application;
    private GraphicsContext graphics;
    private Logger logger;
    private ShapeRenderer2D shapes;
    private UiRoot ui;
    private UiBooleanState showDetails;

    /**
     * Creates a basic application.
     */
    public BasicApplication() {
        this(0L);
    }

    /**
     * Creates a basic application.
     *
     * @param exitAfterFrames the exit after frames
     */
    public BasicApplication(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    /**
     * Initializes the application with the libFDX runtime root.
     *
     * @param fdx the libFDX runtime root
     */
    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        graphics = fdx.graphics().main();
        logger = fdx.logger();
        shapes = new ShapeRenderer2D(graphics);
        showDetails = Ui.state(true);
        ui = new UiToolkit(fdx.files()).root(fdx.displays().main(), graphics).input(fdx.input());
        ui.setContent(root -> {
            root.panel(Ui.modifier().width(260.0f).padding(10.0f).gap(6.0f), panel -> {
                panel.text("LIBFDX BASIC");
                panel.checkbox("DETAILS", showDetails);
                if (showDetails.get()) {
                    panel.text("GRAPHICS: " + graphics.providerId());
                }
            });
        });

        logger.info("libfdx basic sample created with graphics provider " + graphics.providerId());
        logger.info("Basic sample renderer: ShapeRenderer2D");
    }

    /**
     * Handles a size change.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void resize(int width, int height) {
        if (logger != null) {
            logger.info("Resize: " + width + "x" + height);
        }
        if (ui != null) {
            ui.resize(width, height);
        }
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        if (application == null) {
            return;
        }
        if (application.frameId() % 240 == 0) {
            logger.info("Frame " + application.frameId() + " dt=" + application.deltaTime());
        }

        shapes.begin(LoadOp.clear(BACKGROUND_RED, BACKGROUND_GREEN, BACKGROUND_BLUE, BACKGROUND_ALPHA));
        shapes.filledTriangle(0.0f, 0.65f, -0.65f, -0.55f, 0.65f, -0.55f,
                0.95f, 0.76f, 0.28f, 1.0f);
        shapes.end();
        if (ui != null) {
            ui.update(application.deltaTime());
            ui.render();
        }

        if (exitAfterFrames > 0L && application.frameId() >= exitAfterFrames) {
            application.requestExit();
        }
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (shapes != null) {
            shapes.dispose();
            shapes = null;
        }
        if (ui != null) {
            ui.dispose();
            ui = null;
        }
        if (logger != null) {
            logger.info("libfdx basic sample disposed");
        }
    }
}
