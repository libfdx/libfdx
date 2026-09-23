package io.github.libfdx.testsupport;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationListener;

/** Observes one isolated automatic scenario without replacing its completion assertions. */
public final class ManagedTestApplication implements ApplicationListener {
    private final ApplicationListener test;
    private final AutoTestTiming timing;
    private final Runnable onObservationComplete;
    private Application application;
    private boolean observed;
    private boolean disposed;

    public ManagedTestApplication(ApplicationListener test) {
        this(test, () -> {});
    }

    /** Callback runs once on the application thread before requesting automatic shutdown. */
    public ManagedTestApplication(ApplicationListener test, Runnable onObservationComplete) {
        this.test = test;
        this.onObservationComplete = onObservationComplete;
        timing = new AutoTestTiming(
                Float.parseFloat(System.getProperty("libfdx.test.autoDurationSeconds", "6")),
                Integer.parseInt(System.getProperty("libfdx.test.autoStableFrames", "10")),
                Float.parseFloat(System.getProperty("libfdx.test.autoSpikeSeconds", "0.10")),
                Float.parseFloat(System.getProperty("libfdx.test.autoLoadTimeoutSeconds", "15")));
    }

    @Override
    public void create(Fdx fdx) { application = fdx.app(); test.create(fdx); }
    @Override
    public void resize(int width, int height) { test.resize(width, height); }
    @Override
    public void render() { test.render(); }
    @Override
    public void onFrameEnd() {
        test.onFrameEnd();
        if (!observed && timing.update(application.deltaTime())) {
            observed = true;
            onObservationComplete.run();
            application.requestExit();
        }
    }
    @Override
    public void pause() { test.pause(); }
    @Override
    public void resume() { test.resume(); }
    @Override
    public void dispose() { test.dispose(); disposed = true; }

    /** Called only after backend shutdown; early window closure is not a successful run. */
    public void verifyCompleted() {
        if (!observed || !disposed) {
            throw new IllegalStateException("Automatic scenario exited before observation and cleanup completed");
        }
    }
}
