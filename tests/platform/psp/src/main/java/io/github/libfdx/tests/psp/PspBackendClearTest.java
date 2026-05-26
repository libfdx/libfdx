package io.github.libfdx.tests.psp;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.graphics.GraphicsContext;

final class PspBackendClearTest extends ApplicationAdapter {
    private final long exitAfterFrames;
    private Application application;
    private GraphicsContext graphics;
    private long renderedFrames;

    PspBackendClearTest(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        graphics = fdx.graphics().main();
    }

    @Override
    public void render() {
        graphics.clear(0.12f, 0.32f, 0.78f, 1.0f);
        renderedFrames++;
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }
}
