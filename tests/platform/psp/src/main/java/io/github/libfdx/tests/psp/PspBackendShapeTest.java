package io.github.libfdx.tests.psp;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.g2d.ShapeRenderer2D;

final class PspBackendShapeTest extends ApplicationAdapter {
    private final long exitAfterFrames;
    private Application application;
    private GraphicsContext graphics;
    private ShapeRenderer2D shapes;
    private final RenderPassDescriptor renderPassDescriptor = new RenderPassDescriptor().label("psp shape external pass");
    private long renderedFrames;

    PspBackendShapeTest(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        graphics = fdx.graphics().main();
        shapes = new ShapeRenderer2D(graphics, 6);
    }

    @Override
    public void render() {
        GraphicsFrame frame = graphics.currentFrame();
        RenderPass pass = frame.commandEncoder().beginRenderPass(renderPassDescriptor
                .colorAttachment(frame.colorAttachment())
                .colorLoadOp(LoadOp.clear(0.08f, 0.10f, 0.14f, 1.0f))
                .colorStoreOp(StoreOp.store()));
        try {
            shapes.begin(pass);
            shapes.color(0.95f, 0.24f, 0.32f, 1.0f);
            shapes.filledRect(-0.45f, -0.35f, 0.90f, 0.70f);
            shapes.end();
        } finally {
            pass.end();
        }

        renderedFrames++;
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    @Override
    public void dispose() {
        if (shapes != null) {
            shapes.dispose();
            shapes = null;
        }
    }
}
