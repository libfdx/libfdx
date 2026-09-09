package io.github.libfdx.testsupport.android;

import android.os.Process;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsAttachmentRequirements;
import io.github.libfdx.graphics.GraphicsEnvironment;
import java.util.concurrent.CountDownLatch;

/** Launcher-only fault injection after a real native context/surface has been initialized. */
public final class WGPUStartupFaultProvider implements GraphicsAttachmentProvider {
    private final GraphicsAttachmentProvider delegate;
    private final String mode;

    public WGPUStartupFaultProvider(GraphicsAttachmentProvider delegate, String mode) {
        this.delegate = delegate;
        this.mode = mode;
    }

    @Override public ProviderId providerId() { return delegate.providerId(); }
    @Override public GraphicsAttachmentRequirements requirements() { return delegate.requirements(); }

    @Override public GraphicsAttachment create(GraphicsEnvironment environment) {
        GraphicsAttachment attachment = delegate.create(environment);
        System.out.println("[wgpu-startup] native context ready; injected mode=" + mode);
        if(mode.equals("native-crash")) {
            Process.sendSignal(Process.myPid(), 6); // SIGABRT: validate the real Android native-crash exit record.
        } else if(mode.equals("hold")) {
            try { new CountDownLatch(1).await(); }
            catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
        attachment.dispose();
        System.out.println("[wgpu-startup] failed native context disposed");
        throw new FdxException("Injected recoverable startup failure after native initialization");
    }
}
