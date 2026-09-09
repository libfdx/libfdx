package io.github.libfdx.testsupport.android;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.core.FdxException;

/** Exercises disposal/recreation after the first attempt has allocated real rendering resources. */
public final class WGPUStartupFaultListener implements ApplicationListener {
    private final ApplicationListener delegate;
    private final String stage;
    private boolean failed;

    public WGPUStartupFaultListener(ApplicationListener delegate, String stage) {
        this.delegate = delegate;
        this.stage = stage;
    }

    private void failOnce(String currentStage) {
        if (!failed && stage.equals(currentStage)) {
            failed = true;
            throw new FdxException("Injected startup listener failure at " + stage);
        }
    }

    @Override public void create(Fdx fdx) { delegate.create(fdx); failOnce("create"); }
    @Override public void resize(int width, int height) { delegate.resize(width, height); }
    @Override public void render() { delegate.render(); failOnce("frame"); }
    @Override public void onFrameEnd() { delegate.onFrameEnd(); }
    @Override public void pause() { delegate.pause(); }
    @Override public void resume() { delegate.resume(); }
    @Override public void dispose() {
        delegate.dispose();
        System.out.println("[wgpu-startup] listener resources disposed");
    }
}
