package io.github.libfdx.ecs.tooling;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.ecs.World;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;

/** Runs an {@link EcsProject} through the standard portable application lifecycle. */
public final class EcsProjectApplication implements ApplicationListener {
    private final EcsProject project;
    private final EcsRenderContext renderContext = new EcsRenderContext();
    private EcsProjectRuntime runtime;
    private Fdx fdx;
    private boolean active;
    private boolean terminal;

    public EcsProjectApplication(EcsProject project) {
        if (project == null || project.schema() == null) {
            throw new IllegalArgumentException("project and schema cannot be null.");
        }
        this.project = project;
    }

    public EcsProject project() {
        return project;
    }

    public EcsProjectRuntime runtime() {
        requireActive();
        return runtime;
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
        EcsProjectRuntime created = project.createRuntime();
        if (created == null) {
            terminal = true;
            throw new IllegalStateException("Project returned a null runtime.");
        }
        runtime = created;
        try {
            runtime.create(fdx);
            World world = runtime.world();
            if (world == null) {
                throw new IllegalStateException("Project runtime returned a null world after create.");
            }
            active = true;
        } catch (RuntimeException | Error failure) {
            terminal = true;
            cleanupRuntime(failure);
            clearState();
            throw failure;
        }
    }

    @Override
    public void resize(int width, int height) {
        requireActive();
        runtime.resize(width, height);
    }

    @Override
    public void render() {
        requireActive();
        runtime.update(fdx.app().deltaTime());
        GraphicsContext graphics = fdx.graphics().main();
        GraphicsFrame frame = graphics.currentFrame();
        if (frame == null || frame.colorAttachment() == null) {
            throw new IllegalStateException("The main graphics context has no current render frame.");
        }
        renderContext.configure(
                graphics,
                frame,
                frame.colorAttachment(),
                null,
                frame.width(),
                frame.height(),
                runtime.world(),
                runtime.gameCamera(),
                EcsRenderPurpose.GAME);
        runtime.render(renderContext);
    }

    @Override
    public void onFrameEnd() {
        requireActive();
        runtime.onFrameEnd();
    }

    @Override
    public void pause() {
        requireActive();
        runtime.pause();
    }

    @Override
    public void resume() {
        requireActive();
        runtime.resume();
    }

    @Override
    public void dispose() {
        if (terminal) {
            return;
        }
        terminal = true;
        Throwable failure = null;
        if (runtime != null) {
            try {
                runtime.dispose();
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

    private void cleanupRuntime(Throwable failure) {
        if (runtime == null) {
            return;
        }
        try {
            runtime.dispose();
        } catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private void clearState() {
        active = false;
        renderContext.clear();
        runtime = null;
        fdx = null;
    }

    private void requireActive() {
        if (!active || terminal) {
            throw new IllegalStateException("Application is not active.");
        }
    }
}
