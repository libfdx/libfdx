package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.AtmosphericFogControls;
import io.github.libfdx.testsupport.graphics.AtmosphericMist2D;
import io.github.libfdx.testsupport.graphics.FogCourtyard2D;
import io.github.libfdx.testsupport.graphics.FogMovement;
import io.github.libfdx.testsupport.graphics.FogSceneLayout;
import io.github.libfdx.testsupport.graphics.GraphicsParityTest;

import io.github.libfdx.Fdx;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.controller.CameraController2D;
import io.github.libfdx.input.*;
import io.github.libfdx.graphics.LoadOp;

/** Walk through atmospheric mist in a top-down courtyard, with no exploration memory. */
public final class Fog2DTest extends GraphicsParityTest {
    private static final float[][] ROUTE = FogSceneLayout.ROUTE;
    private AtmosphericMist2D mist;
    private final FogMovement movement = new FogMovement();
    private final Camera camera = new Camera().nearFar(.1f, 10);
    private FogCourtyard2D scene;
    private CameraController2D cameraInput;
    private Input input;
    private InputAdapter controls;
    private AtmosphericFogControls hud;
    private int waypoint = 1, movementTaps, touchId = -1, viewHeight;
    private float targetX, targetZ;
    private boolean walkingToTarget, following = true;

    public Fog2DTest(long frames) { super(frames); }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "Fog2DTest");
        input = fdx.input();
        scene = new FogCourtyard2D(graphics);
        mist = new AtmosphericMist2D(graphics);
        FogSceneLayout.populate((model, x, y, z, scale, halfWidth, halfDepth, radius) ->
                movement.obstacle(x, z, halfWidth, halfDepth));
        cameraInput = new CameraController2D(input, camera).touchEnabled(false)
                .pointerRegion(this::insideScene).activationListener(() -> following = false);
        controls = new InputAdapter() {
            @Override
            public boolean keyDown(KeyEvent event) {
                if (event.key() == Key.SPACE) { following = true; return true; }
                int bit = movementBit(event.key());
                movementTaps |= bit;
                return bit != 0;
            }
            @Override
            public boolean pointerDown(PointerEvent event) {
                if (event.button() != MouseButton.LEFT || !insideScene(event.x(), event.y())) return false;
                destination(event.x(), event.y());
                return true;
            }
            @Override
            public boolean touchDown(TouchEvent event) {
                TouchPoint point = event.point();
                if (point == null || touchId != -1 || !insideScene(point.x(), point.y())) return false;
                touchId = point.id();
                destination(point.x(), point.y());
                return true;
            }
            @Override
            public boolean touchMoved(TouchEvent event) {
                TouchPoint point = event.point();
                if (point == null || point.id() != touchId) return false;
                destination(point.x(), point.y());
                return true;
            }
            @Override
            public boolean touchUp(TouchEvent event) {
                if (event.point() == null || event.point().id() != touchId) return false;
                touchId = -1;
                return true;
            }
        };
        input.addProcessor(controls);
        hud = new AtmosphericFogControls(fdx, "MISTY COURTYARD  /  2D",
                "Walk through the mist. Nearby scenery becomes easier to see.",
                "WASD / arrows / tap to walk | Right-drag to pan | Wheel to zoom", true, this::restart);
        hud.autoWalk.set(exitAfterFrames != 0);
        updateViewport();
        restart();
        markCreated();
        logger.info("Fog2DTest: 48x40 courtyard, solid obstacles and atmospheric mist; provider="
                + graphics.providerId().value());
    }

    private boolean insideScene(int x, int y) {
        return x >= 0 && x < display.width() && y > 120 && y < display.height() - 150;
    }

    private void destination(int x, int y) {
        targetX = camera.position().x() + (x - display.width() * .5f) * camera.zoom();
        targetZ = -camera.position().y() + (y - display.height() * .5f) * camera.zoom();
        walkingToTarget = true;
        hud.autoWalk.set(false);
    }

    private static int movementBit(Key key) {
        return switch (key) {
            case D, RIGHT -> 1; case A, LEFT -> 2; case W, UP -> 4; case S, DOWN -> 8; default -> 0;
        };
    }

    private void restart() {
        movement.x = ROUTE[0][0]; movement.z = ROUTE[0][1]; waypoint = 1;
        movementTaps = 0; walkingToTarget = false; following = true;
        camera.position(movement.x, -movement.z + 1.6f, 1).update();
    }

    private void updateViewport() {
        int width = Math.max(1, display.width()), height = Math.max(1, display.height());
        float worldHeight = viewHeight == 0 ? 20 : camera.zoom() * viewHeight;
        if (height != viewHeight) camera.zoom(worldHeight / height);
        viewHeight = height;
        camera.viewport(width, height);
        cameraInput.zoomRange(12f / height, 38f / height);
    }

    private boolean walkToward(float x, float z, float speed, float delta) {
        float dx = x - movement.x, dz = z - movement.z;
        float distance = (float)Math.sqrt(dx * dx + dz * dz);
        if (distance < .08f) return true;
        float step = Math.min(distance, speed * delta);
        movement.move(dx / distance * step, dz / distance * step);
        return false;
    }

    @Override
    public void render() {
        float delta = Math.min(application.deltaTime(), .05f);
        if (hud.autoWalk.get()) { walkingToTarget = false; following = true; }
        float sideways = (input.isKeyPressed(Key.D) || input.isKeyPressed(Key.RIGHT) || (movementTaps & 1) != 0 ? 1 : 0)
                - (input.isKeyPressed(Key.A) || input.isKeyPressed(Key.LEFT) || (movementTaps & 2) != 0 ? 1 : 0);
        float forward = (input.isKeyPressed(Key.W) || input.isKeyPressed(Key.UP) || (movementTaps & 4) != 0 ? 1 : 0)
                - (input.isKeyPressed(Key.S) || input.isKeyPressed(Key.DOWN) || (movementTaps & 8) != 0 ? 1 : 0);
        movementTaps = 0;
        if (sideways != 0 || forward != 0) {
            hud.autoWalk.set(false); walkingToTarget = false; following = true;
            float distance = 3.6f * delta / Math.max(1, (float)Math.sqrt(sideways * sideways + forward * forward));
            movement.move(sideways * distance, -forward * distance);
        } else if (walkingToTarget) {
            float previousX = movement.x, previousZ = movement.z;
            if (walkToward(targetX, targetZ, 3.6f, delta)
                    || (delta > 0 && previousX == movement.x && previousZ == movement.z)) walkingToTarget = false;
        } else if (hud.autoWalk.get() && walkToward(ROUTE[waypoint][0], ROUTE[waypoint][1], 2.7f, delta)) {
            logger.info("Fog2DTest patrol reached waypoint " + waypoint);
            waypoint = (waypoint + 1) % ROUTE.length;
        }
        updateViewport();
        if (following) {
            float blend = 1 - (float)Math.exp(-delta * 9);
            camera.position(camera.position().x() + (movement.x - camera.position().x()) * blend,
                    camera.position().y() + (-movement.z + 1.6f - camera.position().y()) * blend, 1);
        }
        cameraInput.update(delta);
        scene.renderWorld(camera, movement.x, movement.z, walkingToTarget, targetX, targetZ,
                LoadOp.clear(AtmosphericMist2D.RED, AtmosphericMist2D.GREEN, AtmosphericMist2D.BLUE, 1));
        if (hud.enabled.get()) mist.render(camera, movement.x, movement.z, hud.strength.get(), hud.circleRadius.get());
        hud.render(delta); finishFrame();
    }

    @Override
    public void resize(int width, int height) { if (hud != null) hud.resize(width, height); }

    @Override
    public void dispose() {
        if (input != null && controls != null) input.removeProcessor(controls);
        dispose(hud); dispose(cameraInput); dispose(scene); dispose(mist);
        verifyDisposed();
    }
}
