package io.github.libfdx.ecs.tooling;

import io.github.libfdx.ecs.World;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.graphics.camera.Camera;

/** Mutable host-owned render context intended for bounded reuse. */
public final class EcsRenderContext {
    private GraphicsContext graphics;
    private GraphicsFrame frame;
    private TextureView colorTarget;
    private TextureView depthTarget;
    private World world;
    private Camera camera;
    private EcsRenderPurpose purpose;
    private int width;
    private int height;

    public EcsRenderContext configure(
            GraphicsContext graphics,
            GraphicsFrame frame,
            TextureView colorTarget,
            TextureView depthTarget,
            int width,
            int height,
            World world,
            Camera camera,
            EcsRenderPurpose purpose) {
        if (graphics == null || frame == null || colorTarget == null || world == null || purpose == null) {
            throw new IllegalArgumentException("graphics, frame, colorTarget, world, and purpose cannot be null.");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Render dimensions must be positive.");
        }
        this.graphics = graphics;
        this.frame = frame;
        this.colorTarget = colorTarget;
        this.depthTarget = depthTarget;
        this.width = width;
        this.height = height;
        this.world = world;
        this.camera = camera;
        this.purpose = purpose;
        return this;
    }

    public GraphicsContext graphics() {
        requireConfigured();
        return graphics;
    }

    public GraphicsFrame frame() {
        requireConfigured();
        return frame;
    }

    public TextureView colorTarget() {
        requireConfigured();
        return colorTarget;
    }

    public TextureView depthTarget() {
        requireConfigured();
        return depthTarget;
    }

    public int width() {
        requireConfigured();
        return width;
    }

    public int height() {
        requireConfigured();
        return height;
    }

    public World world() {
        requireConfigured();
        return world;
    }

    public Camera camera() {
        requireConfigured();
        return camera;
    }

    public EcsRenderPurpose purpose() {
        requireConfigured();
        return purpose;
    }

    public boolean isConfigured() {
        return graphics != null;
    }

    public void clear() {
        graphics = null;
        frame = null;
        colorTarget = null;
        depthTarget = null;
        world = null;
        camera = null;
        purpose = null;
        width = 0;
        height = 0;
    }

    private void requireConfigured() {
        if (graphics == null) {
            throw new IllegalStateException("Render context is not configured.");
        }
    }
}
