package io.github.libfdx.tests.psp;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.input.Gamepad;
import io.github.libfdx.input.GamepadAxis;
import io.github.libfdx.input.GamepadButton;
import io.github.libfdx.input.Input;

final class PspBackendInputTest extends ApplicationAdapter {
    private final long exitAfterFrames;
    private Application application;
    private Display display;
    private Input input;
    private Gamepad gamepad;
    private SpriteBatch spriteBatch;
    private Texture marker;
    private long renderedFrames;

    PspBackendInputTest(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        display = fdx.displays().main();
        input = fdx.input();
        if (input != null && input.gamepads() != null) {
            gamepad = input.gamepads().find(0);
        }
        GraphicsContext graphics = fdx.graphics().main();
        spriteBatch = new SpriteBatch(graphics, 6);
        marker = graphics.device().createTexture(TextureDescriptor.rgba8("psp input marker", 128, 128));
        graphics.device().writeTexture(marker, PspCheckerTexture.pixels(128, 16, 4));
    }

    @Override
    public void render() {
        boolean connected = input != null && input.capabilities().supportsGamepads()
                && gamepad != null && gamepad.isConnected();
        LoadOp clear = connected
                ? LoadOp.clear(0.82f, 0.96f, 0.84f, 1.0f)
                : LoadOp.clear(0.72f, 0.02f, 0.02f, 1.0f);

        spriteBatch.begin(clear);
        spriteBatch.viewport(display.framebufferWidth(), display.framebufferHeight());
        tintForButtons();
        spriteBatch.draw(marker, xPosition(), yPosition(), 0.42f, 0.42f);
        spriteBatch.end();

        renderedFrames++;
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    private float xPosition() {
        if (gamepad == null) {
            return -0.21f;
        }
        float x = gamepad.axis(GamepadAxis.LEFT_X) * 0.68f;
        if (gamepad.pressed(GamepadButton.DPAD_LEFT)) {
            x -= 0.45f;
        }
        if (gamepad.pressed(GamepadButton.DPAD_RIGHT)) {
            x += 0.45f;
        }
        return clamp(x, -0.78f, 0.36f);
    }

    private float yPosition() {
        if (gamepad == null) {
            return -0.21f;
        }
        float y = -gamepad.axis(GamepadAxis.LEFT_Y) * 0.68f;
        if (gamepad.pressed(GamepadButton.DPAD_UP)) {
            y += 0.45f;
        }
        if (gamepad.pressed(GamepadButton.DPAD_DOWN)) {
            y -= 0.45f;
        }
        return clamp(y, -0.78f, 0.36f);
    }

    private void tintForButtons() {
        if (gamepad == null) {
            spriteBatch.color(0.35f, 0.35f, 0.35f, 1.0f);
            return;
        }
        if (gamepad.pressed(GamepadButton.SOUTH)) {
            spriteBatch.color(1.0f, 0.25f, 0.25f, 1.0f);
        } else if (gamepad.pressed(GamepadButton.EAST)) {
            spriteBatch.color(0.25f, 0.85f, 0.25f, 1.0f);
        } else if (gamepad.pressed(GamepadButton.WEST)) {
            spriteBatch.color(0.25f, 0.45f, 1.0f, 1.0f);
        } else if (gamepad.pressed(GamepadButton.NORTH)) {
            spriteBatch.color(1.0f, 0.85f, 0.20f, 1.0f);
        } else {
            spriteBatch.color(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }

    @Override
    public void dispose() {
        if (spriteBatch != null) {
            spriteBatch.dispose();
            spriteBatch = null;
        }
        if (marker != null) {
            marker.dispose();
            marker = null;
        }
    }
}
