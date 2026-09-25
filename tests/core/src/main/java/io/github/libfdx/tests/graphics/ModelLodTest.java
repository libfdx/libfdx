package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.testsupport.graphics.GraphicsParityTest;
import io.github.libfdx.testsupport.graphics.lod.ModelLodControls;
import io.github.libfdx.testsupport.graphics.lod.ModelLodLayout;
import io.github.libfdx.testsupport.graphics.lod.ModelLodScene;
import io.github.libfdx.testsupport.graphics.lod.ModelLodVerification;

/** Interactive full-detail/LOD comparison and automatic UI-driven rendering regression. */
public final class ModelLodTest extends GraphicsParityTest {
    private final ModelLodLayout layout = new ModelLodLayout();
    private ModelLodScene scene;
    private ModelLodControls controls;
    private ModelLodVerification verification;
    private int width, height, pixelWidth, pixelHeight, uiWidth, uiHeight;
    private boolean capturedFrame, failed;

    public ModelLodTest(long frames) { super(frames); }

    @Override public void create(Fdx fdx) {
        initialize(fdx,"ModelLodTest");
        scene = new ModelLodScene(graphics);
        DefaultInput scripted = requiresCompletion() ? new DefaultInput() : null;
        controls = new ModelLodControls(fdx,scripted != null ? scripted : fdx.input(),layout,scene);
        if (scripted != null) verification = new ModelLodVerification(controls,scene,layout,scripted,logger);
        updateLayout();
        markCreated();
    }

    private void updateLayout() {
        int w = Math.max(1,display.width()), h = Math.max(1,display.height());
        int pw = framebufferWidth(), ph = framebufferHeight();
        // UI Kit sizes controls in DPI-scaled coordinates, not window pixels.
        int uw = Math.max(1,(int)Math.ceil(controls.root().uiX(w)));
        int uh = Math.max(1,(int)Math.ceil(controls.root().uiY(h)));
        if (width == w && height == h && pixelWidth == pw && pixelHeight == ph
                && uiWidth == uw && uiHeight == uh) return;
        width = w; height = h; pixelWidth = pw; pixelHeight = ph;
        uiWidth = uw; uiHeight = uh;
        layout.update(uw,uh,pw,ph); controls.resize(w,h);
    }

    @Override public void render() {
        try {
            updateLayout();
            float delta = Math.min(.1f,application.deltaTime());
            controls.update(delta);
            if (verification != null) verification.beforeFrame();
            scene.render(controls,layout,delta);
            controls.render();
            capturedFrame = verification != null && verification.afterFrame(graphics);
            finishFrame();
        } catch (RuntimeException | Error error) {
            failed = true;
            throw error;
        }
    }

    @Override protected boolean readyForCapture() { return !capturedFrame && scene != null && scene.ready() && (verification == null || verification.complete()); }

    @Override public void dispose() {
        try {
            if (!failed && verification != null) verification.requireComplete();
        } finally {
            try { dispose(controls); } finally { dispose(scene); }
        }
        if (!failed) verifyDisposed();
    }
}
