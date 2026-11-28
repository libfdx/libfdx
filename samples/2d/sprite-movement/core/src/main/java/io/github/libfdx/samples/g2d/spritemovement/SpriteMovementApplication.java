package io.github.libfdx.samples.g2d.spritemovement;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.g2d.*;
import io.github.libfdx.input.Input;
import io.github.libfdx.samples.g2d.spritemovement.input.HarborControls;
import io.github.libfdx.samples.g2d.spritemovement.render.SpriteMovementTextures;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiToolkit;

/** Tidepost runs the same chart, steering and postal route on every backend. */
public final class SpriteMovementApplication extends ApplicationAdapter {
    private final long exitAfterFrames;
    private final SpriteMovementState state = new SpriteMovementState();
    private final RenderPassDescriptor descriptor = new RenderPassDescriptor().label("Tidepost harbor");
    private static final LoadOp SEA = LoadOp.clear(.08f, .39f, .4f, 1);
    private Application application;
    private GraphicsContext graphics;
    private Display display;
    private Input input;
    private HarborControls controls;
    private SpriteBatch batch;
    private SpriteMovementTextures art;
    private UiRoot ui;
    private DispatchCard card;
    private boolean sailing, paused, startRequested;
    private float time, left, top, viewWidth, viewHeight;

    public SpriteMovementApplication() {
        this(0);
    }

    public SpriteMovementApplication(long exitAfterFrames) {
        this.exitAfterFrames = Math.max(0, exitAfterFrames);
    }

    @Override
    public void create(Fdx fdx) {
        if (fdx == null) throw new IllegalArgumentException("fdx required");
        application = fdx.app();
        graphics = fdx.graphics().main();
        display = fdx.displays().main();
        input = fdx.input();
        try {
            batch = new SpriteBatch(graphics);
            art = new SpriteMovementTextures(fdx.files(), graphics);
            controls = new HarborControls(input);
            card = new DispatchCard(art.mail, () -> startRequested = true);
            ui = new UiToolkit(fdx.files()).root(display, graphics).autoUiScale(false).input(input);
            DispatchCard.theme(ui);
            ui.setContent(card::build);
            input.addProcessor(controls);
        } catch (RuntimeException | Error failed) {
            try { dispose(); } catch (RuntimeException | Error cleanup) { failed.addSuppressed(cleanup); }
            throw failed;
        }
    }

    @Override
    public void render() {
        if (application == null) return;
        GraphicsFrame frame = graphics.currentFrame();
        if (frame == null || frame.width() <= 0 || frame.height() <= 0) return;
        card.layout(ui, display, frame.width(), frame.height());
        float dt = paused ? 0 : Math.max(0, Math.min(application.deltaTime(), .12f));
        controls.poll();
        boolean confirm = controls.takeConfirm();
        if (!paused && !sailing && (startRequested || confirm)) {
            state.reset();
            sailing = true;
            controls.sailing(true);
            card.stop(0);
        }
        startRequested = false;
        if (sailing && !paused) {
            state.update(controls, dt);
            card.stop(state.delivered);
            if (state.delivered == SpriteMovementState.ROUTE.length) {
                sailing = false;
                controls.sailing(false);
            }
        }
        time = (time + dt) % 60;
        draw(frame);
        ui.update(dt);
        ui.render();
        if (exitAfterFrames > 0 && application.frameId() >= exitAfterFrames) application.requestExit();
    }

    private void draw(GraphicsFrame frame) {
        float aspect = (float) frame.width() / frame.height();
        viewWidth = Math.min(SpriteMovementState.WIDTH, SpriteMovementState.HEIGHT * aspect);
        viewHeight = viewWidth / aspect;
        left = clamp(state.x - viewWidth / 2, 0, SpriteMovementState.WIDTH - viewWidth);
        top = clamp(state.y - viewHeight / 2, 0, SpriteMovementState.HEIGHT - viewHeight);
        batch.viewport(frame.width(), frame.height());
        RenderPass pass = frame.commandEncoder().beginRenderPass(descriptor.colorAttachment(frame.colorAttachment())
                .colorLoadOp(SEA).colorStoreOp(StoreOp.store()));
        boolean begun = false;
        Throwable failure = null;
        try {
            batch.begin(pass);
            begun = true;
            batch.color(1, 1, 1, 1);
            batch.drawUv(art.harbor, left / SpriteMovementState.WIDTH, top / SpriteMovementState.HEIGHT,
                    (left + viewWidth) / SpriteMovementState.WIDTH, (top + viewHeight) / SpriteMovementState.HEIGHT,
                    -1, -1, 2, 2);
            for (int i = 0; i < SpriteMovementState.WAKE_COUNT; i++) {
                float age = state.wakeAge[i];
                if (age >= 1) continue;
                batch.color(.86f, 1, .96f, (1 - age) * .95f);
                sprite(art.wake, state.wakeX[i], state.wakeY[i], 9 + age * 23, 13 + age * 7, state.wakeHeading[i]);
            }
            if (sailing) {
                if (state.delivered > 0 && state.deliveryAge < 1) {
                    SpriteMovementState.Dock previous = SpriteMovementState.ROUTE[state.delivered - 1];
                    float age = state.deliveryAge;
                    batch.color(1, .9f, .58f, 1 - age);
                    sprite(art.ripple, previous.x(), previous.y(), 16 + age * 44, 12 + age * 28, 0);
                }
                drawDestination();
            }
            batch.color(.05f, .19f, .20f, .44f);
            sprite(art.launch, state.x + 1.5f, state.y + 2, 10.8f, 25, state.heading);
            batch.color(1, 1, 1, 1);
            sprite(art.launch, state.x, state.y, 10.8f, 25, state.heading);
        } catch (RuntimeException | Error failed) {
            failure = failed;
            throw failed;
        } finally {
            finishRender(batch, pass, begun, failure);
        }
    }

    private void drawDestination() {
        SpriteMovementState.Dock dock = SpriteMovementState.ROUTE[state.delivered];
        float x = dock.x(), y = dock.y();
        if (x < left + 12 || x > left + viewWidth - 12 || y < top + 26 || y > top + viewHeight - 12) {
            float px = clamp(x, left + 14, left + viewWidth - 14);
            float py = clamp(y, top + 18, top + viewHeight - 16);
            float bearing = (float) Math.toDegrees(Math.atan2(x - px, py - y));
            batch.color(1, 1, 1, 1);
            sprite(art.bearing, px, py, 17, 17, bearing);
            return;
        }
        float pulse = time % 1.5f / 1.5f;
        batch.color(1, .94f, .72f, .86f * (1 - pulse));
        sprite(art.ripple, x, y, 15 + pulse * 14, 10 + pulse * 9, 0);
        batch.color(1, 1, 1, 1);
        sprite(art.mail, x, y - 11 + (float) Math.sin(time * 3) * .7f, 18, 20, 0);
    }

    private void sprite(TextureRegion region, float x, float y, float w, float h, float heading) {
        float width = toClipSize(w, viewWidth), height = toClipSize(h, viewHeight);
        batch.draw(region, toClipCenter(x, left + viewWidth / 2, viewWidth) - width / 2,
                toClipCenter(top + viewHeight / 2, y, viewHeight) - height / 2, width, height,
                width / 2, height / 2, -heading);
    }

    public static float toClipSize(float worldSize, float viewportSize) {
        return 2 * Math.abs(worldSize) / Math.max(.01f, viewportSize);
    }

    public static float toClipCenter(float worldPosition, float cameraPosition, float viewportSize) {
        return 2 * (worldPosition - cameraPosition) / Math.max(.01f, viewportSize);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void pause() {
        paused = true;
        startRequested = false;
        if (controls != null) controls.sailing(false);
    }

    @Override
    public void resume() {
        paused = false;
        if (controls != null) controls.sailing(sailing);
    }

    @Override
    public void dispose() {
        if (controls != null) input.removeProcessor(controls);
        controls = null;
        application = null;
        Throwable failure = close(ui, null);
        ui = null;
        failure = close(art, failure);
        art = null;
        failure = close(batch, failure);
        batch = null;
        rethrowFailure(failure);
    }

    private static Throwable close(Disposable resource, Throwable failure) {
        if (resource != null) {
            try { resource.dispose(); }
            catch (RuntimeException | Error cleanup) { return aggregateFailure(failure, cleanup); }
        }
        return failure;
    }

    static void finishRender(Batch2D batch, RenderPass pass, boolean batchBegun, Throwable renderFailure) {
        Throwable failure = renderFailure;
        if (batchBegun) {
            try {
                batch.end();
            } catch (RuntimeException | Error batchFailure) {
                failure = aggregateFailure(failure, batchFailure);
            }
        }
        try {
            pass.end();
        } catch (RuntimeException | Error passFailure) {
            failure = aggregateFailure(failure, passFailure);
        }
        if (renderFailure == null) {
            rethrowFailure(failure);
        }
    }

    private static Throwable aggregateFailure(Throwable failure, Throwable next) {
        if (failure == null) {
            return next;
        }
        if (failure != next) {
            failure.addSuppressed(next);
        }
        return failure;
    }

    private static void rethrowFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }
}
