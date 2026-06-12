package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.g2d.ShapeRenderer2D;
import io.github.libfdx.tests.TestFpsLogger;

/**
 * Runs the shape render test scenario.
 *
 * @author xpenatan
 */
abstract class ShapeRenderTest extends ApplicationAdapter {
    private static final float BACKGROUND_RED = 1.0f;
    private static final float BACKGROUND_GREEN = 1.0f;
    private static final float BACKGROUND_BLUE = 1.0f;
    private static final float BACKGROUND_ALPHA = 1.0f;

    private final String testName;
    private final long exitAfterFrames;
    private Application application;
    private GraphicsContext graphics;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private ShapeRenderer2D shapes;
    private boolean created;
    private long renderedFrames;

    ShapeRenderTest(String testName, long exitAfterFrames) {
        this.testName = testName;
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
        fpsLogger = TestFpsLogger.create(logger, testLabel());
        shapes = new ShapeRenderer2D(graphics);

        created = true;
        logger.info(testName + " created with graphics provider " + graphics.providerId()
                + " and ShapeRenderer2D");
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        float deltaSeconds = application.deltaTime();
        shapes.begin(LoadOp.clear(BACKGROUND_RED, BACKGROUND_GREEN, BACKGROUND_BLUE, BACKGROUND_ALPHA));
        renderShape(shapes);
        shapes.end();

        renderedFrames++;
        fpsLogger.frame(deltaSeconds, renderedFrames);
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    abstract void renderShape(ShapeRenderer2D shapes);

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (shapes != null) {
            shapes.dispose();
            shapes = null;
        }
        if (!created) {
            throw new FdxException(testName + " did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException(testName + " rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        logger.info(testName + " rendered " + renderedFrames + " frames");
    }

    private String testLabel() {
        if (testName == null || testName.length() == 0) {
            return "ShapeRenderTest";
        }
        return Character.toUpperCase(testName.charAt(0)) + testName.substring(1) + "Test";
    }
}
