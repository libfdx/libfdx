package io.github.libfdx.ecs;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.TextureView;

/**
 * Runs an {@link EcsProject} through the standard portable application lifecycle.
 *
 * <p>The adapter owns the world. The project only initializes it; systems
 * advance simulation and record game/UI rendering through the world's phases,
 * while manager/system detachment releases project-owned resources.</p>
 */
public final class EcsApplication implements ApplicationListener {
    private final EcsProject project;
    private final World world = new World();
    private Fdx fdx;
    private boolean active;
    private boolean terminal;

    public EcsApplication(EcsProject project) {
        if (project == null) {
            throw new IllegalArgumentException("project cannot be null.");
        }
        this.project = project;
    }

    public EcsProject project() {
        return project;
    }

    public World world() {
        return world;
    }

    @Override
    public void create(Fdx fdx) {
        if (fdx == null) {
            throw new IllegalArgumentException("fdx cannot be null.");
        }
        if (active || terminal) {
            throw new IllegalStateException("Application cannot be created again.");
        }
        this.fdx = fdx;
        try {
            project.initialize(fdx, world);
            world.flushCommands();
            active = true;
        } catch (RuntimeException | Error failure) {
            terminal = true;
            cleanupWorld(failure);
            clearState();
            throw failure;
        }
    }

    @Override
    public void resize(int width, int height) {
        requireActive();
    }

    @Override
    public void render() {
        requireActive();
        world.update(fdx.app().deltaTime());

        if (world.renderSystemCount() == 0 && world.uiRenderSystemCount() == 0) {
            return;
        }
        GraphicsContext graphics = fdx.graphics().main();
        GraphicsFrame frame = graphics.currentFrame();
        TextureView colorTarget = frame != null ? frame.colorAttachment() : null;
        if (colorTarget == null) {
            throw new IllegalStateException("The main graphics context has no current render frame.");
        }
        int width = frame.width();
        int height = frame.height();
        world.render(
                frame,
                colorTarget,
                null,
                width,
                height,
                null);
        world.renderUi(
                frame,
                colorTarget,
                null,
                width,
                height,
                null);
    }

    @Override
    public void onFrameEnd() {
        requireActive();
    }

    @Override
    public void pause() {
        requireActive();
    }

    @Override
    public void resume() {
        requireActive();
    }

    @Override
    public void dispose() {
        if (terminal) {
            return;
        }
        terminal = true;
        Throwable failure = null;
        if (active) {
            try {
                world.discardCommands();
                world.clear();
                world.flushCommands();
            } catch (RuntimeException | Error disposeFailure) {
                failure = disposeFailure;
            }
        }
        clearState();
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    private void cleanupWorld(Throwable failure) {
        try {
            world.discardCommands();
            world.clear();
            world.flushCommands();
        } catch (RuntimeException | Error cleanupFailure) {
            if (failure != cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    private void clearState() {
        active = false;
        fdx = null;
    }

    private void requireActive() {
        if (!active || terminal) {
            throw new IllegalStateException("Application is not active.");
        }
    }
}
