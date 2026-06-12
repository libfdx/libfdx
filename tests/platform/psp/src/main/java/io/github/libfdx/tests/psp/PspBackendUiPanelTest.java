package io.github.libfdx.tests.psp;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiToolkit;

/**
 * Runs the psp backend ui panel test scenario.
 *
 * @author xpenatan
 */
final class PspBackendUiPanelTest extends ApplicationAdapter {
    private final long exitAfterFrames;
    private Application application;
    private GraphicsContext graphics;
    private UiRoot root;
    private long renderedFrames;

    PspBackendUiPanelTest(long exitAfterFrames) {
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
        Display display = fdx.displays().main();
        graphics = fdx.graphics().main();
        root = new UiToolkit(fdx.files())
                .root(display, graphics)
                .autoUiScale(false)
                .uiScale(1.0f);
        root.setContent(this::buildUi);
    }

    /**
     * Handles a size change.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void resize(int width, int height) {
        if (root != null) {
            root.resize(width, height);
        }
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        graphics.clear(0.04f, 0.05f, 0.07f, 1.0f);
        root.update(application.deltaTime());
        root.render();

        renderedFrames++;
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    private void buildUi(UiScope ui) {
        ui.panel(Ui.modifier().width(320.0f).height(160.0f).margin(80.0f).padding(12.0f), panel -> {
            panel.text("A");
        });
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (root != null) {
            root.dispose();
            root = null;
        }
    }
}
