package io.github.libfdx.tests;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.tests.utils.uikittest.UiKitTestSession;

/**
 * Runs the UI Kit showcase and its validation scenarios.
 *
 * @author xpenatan
 */
public final class UiKitTest extends ApplicationAdapter {
    private final UiKitTestSession session;

    public UiKitTest(long exitAfterFrames) {
        session = new UiKitTestSession(exitAfterFrames);
    }

    @Override
    public void create(Fdx fdx) {
        session.create(fdx);
    }

    @Override
    public void resize(int width, int height) {
        session.resize(width, height);
    }

    @Override
    public void render() {
        session.render();
    }

    @Override
    public void dispose() {
        session.dispose();
    }

}
