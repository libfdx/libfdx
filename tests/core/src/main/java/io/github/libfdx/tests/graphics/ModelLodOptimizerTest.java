package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.assets.AssetExecutor;
import io.github.libfdx.graphics.meshoptimizer.MeshLodSimplifier;
import io.github.libfdx.graphics.meshoptimizer.PortableMeshLodSimplifier;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.testsupport.graphics.GraphicsParityTest;
import io.github.libfdx.testsupport.graphics.lodoptimizer.*;

/** Loads real models and generates configurable LODs with portable or explicitly injected generation. */
public final class ModelLodOptimizerTest extends GraphicsParityTest {
    private final MeshLodSimplifier simplifier;
    private final AssetExecutor executor;
    private LodOptimizerSession session;
    private LodOptimizerPreview preview;
    private LodOptimizerControls controls;
    private LodOptimizerVerification verification;
    private int width,height,pixelWidth,pixelHeight;
    private boolean failed,capturedFrame;
    private long started;

    public ModelLodOptimizerTest(long frames) { this(frames,new PortableMeshLodSimplifier(),null); }
    /** Takes ownership of the optional worker executor. */
    public ModelLodOptimizerTest(long frames,MeshLodSimplifier simplifier,AssetExecutor executor) {
        super(frames); this.simplifier=simplifier; this.executor=executor;
    }
    @Override public void create(Fdx fdx) {
        initialize(fdx,"ModelLodOptimizerTest");
        session = new LodOptimizerSession(fdx,simplifier,executor);
        preview = new LodOptimizerPreview(graphics);
        DefaultInput scripted = requiresCompletion()?new DefaultInput():null;
        controls = new LodOptimizerControls(fdx,scripted==null?fdx.input():scripted,session,preview);
        if (scripted!=null) {
            verification = new LodOptimizerVerification(controls,session,preview,scripted,logger);
        } else session.load(controls.path.get());
        resizeIfNeeded(); started=System.nanoTime(); markCreated();
    }
    private void resizeIfNeeded() {
        int w=Math.max(1,display.width()),h=Math.max(1,display.height()),pw=framebufferWidth(),ph=framebufferHeight();
        if (w==width && h==height && pw==pixelWidth && ph==pixelHeight) return;
        width=w; height=h; pixelWidth=pw; pixelHeight=ph; controls.resize(w,h,pw,ph);
    }
    @Override public void render() {
        try {
            resizeIfNeeded(); session.update(); controls.update(Math.min(.1f,application.deltaTime()));
            if (verification!=null) verification.beforeFrame();
            session.animate(verification==null?Math.min(.1f,application.deltaTime()):0);
            preview.render(session,controls.distance.get(),controls.previewLevel.get(),controls.yaw.get()); controls.render();
            capturedFrame = verification!=null && verification.afterFrame(graphics);
            // The requested stable frame count starts after asynchronous load/generation checks finish.
            if (verification==null || verification.complete()) finishFrame();
            else if (System.nanoTime()-started>90_000_000_000L) throw new IllegalStateException("LOD optimizer validation timed out: "+session.status());
        } catch (RuntimeException | Error failure) { failed=true; throw failure; }
    }
    @Override protected boolean readyForCapture() { return !capturedFrame && preview.ready() && (verification==null || verification.complete()); }
    @Override public void dispose() {
        try { if (!failed && verification!=null) verification.requireComplete(); }
        finally { try { dispose(controls); } finally { try { dispose(preview); } finally { dispose(session); } } }
        if (!failed) verifyDisposed();
    }
}
