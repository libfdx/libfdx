package io.github.libfdx.samples.g2d.spritemovement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.assets.loaders.AtlasData;
import io.github.libfdx.samples.g2d.spritemovement.render.SpriteMovementTextures;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.g2d.Batch2D;
import io.github.libfdx.samples.g2d.spritemovement.input.MovementInput;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

final class SpriteMovementApplicationTest {
    private static final float EPSILON = 0.000001f;

    @Test
    void helmPreservesAnalogSpeedAndNormalizesDiagonals() {
        MutableMovementInput input = new MutableMovementInput();
        SpriteMovementState state = new SpriteMovementState();
        input.horizontal = 0.5f;
        advance(state, input, 32);
        assertEquals(SpriteMovementState.SPEED / 2, state.vx, EPSILON);
        input.horizontal = 1;
        input.vertical = 1;
        advance(state, input, 32);
        assertEquals(SpriteMovementState.SPEED, Math.hypot(state.vx, state.vy), .001f);
        assertEquals(state.vx, -state.vy, .001f);
        input.horizontal = input.vertical = 0;
        advance(state, input, 32);
        assertEquals(0, state.vx, EPSILON);
        assertEquals(0, state.vy, EPSILON);
    }

    @Test
    void boatStopsAtTheIslandAndWakeDissipates() {
        SpriteMovementState state = new SpriteMovementState();
        MutableMovementInput input = new MutableMovementInput();
        input.vertical = 1;
        advance(state, input, 180);
        assertTrue(state.y > 153 && state.y < 165);
        assertTrue(SpriteMovementState.water(state.x, state.y));
        assertEquals(0, state.vy, EPSILON);
        for (float age : state.wakeAge) assertEquals(1, age, EPSILON);
    }

    @Test
    void routeCanBeSailedInOrderAndRestarted() {
        SpriteMovementState state = new SpriteMovementState();
        MutableMovementInput input = new MutableMovementInput();
        for (int i = 0; i < SpriteMovementState.ROUTE.length; i++) {
            SpriteMovementState.Dock dock = SpriteMovementState.ROUTE[i];
            assertTrue(SpriteMovementState.water(dock.x(), dock.y()), dock.name());
            for (int frame = 0; frame < 500 && state.delivered == i; frame++) {
                float dx = dock.x() - state.x, dy = dock.y() - state.y;
                float distance = (float) Math.hypot(dx, dy);
                input.horizontal = dx / distance;
                input.vertical = -dy / distance;
                state.update(input, 1f / 60);
                assertTrue(SpriteMovementState.water(state.x, state.y));
            }
            assertEquals(i + 1, state.delivered, dock.name());
        }
        state.reset();
        assertEquals(0, state.delivered);
        assertEquals(SpriteMovementState.START_X, state.x);
        assertEquals(SpriteMovementState.START_Y, state.y);
    }

    @Test
    void harborAndPackedSpritesAreBundled() throws Exception {
        Path assets = Path.of("assets");
        assertTrue(Files.isRegularFile(assets.resolve(SpriteMovementTextures.CHART)));
        Path atlasFile = assets.resolve(SpriteMovementTextures.ATLAS);
        AtlasData atlas = AtlasData.parse(Files.readAllBytes(atlasFile));
        assertTrue(Files.isRegularFile(atlasFile.resolveSibling(atlas.page(0).image())));
        for (String name : new String[]{"launch", "wake", "ripple", "mail", "bearing"}) {
            assertTrue(atlas.indexOf(name) >= 0, name);
        }
    }

    private static void advance(SpriteMovementState state, MovementInput input, int frames) {
        for (int i = 0; i < frames; i++) state.update(input, 1f / 60);
    }

    @Test
    void runtimeMapsWorldBoundsIntoSpriteBatchClipSpace() {
        float viewportWidth = 8.0f;
        float viewportHeight = 6.0f;
        float playerWidth = SpriteMovementApplication.toClipSize(1.0f, viewportWidth);
        float playerHeight = SpriteMovementApplication.toClipSize(1.0f, viewportHeight);

        assertEquals(0.25f, playerWidth, EPSILON);
        assertEquals(1.0f / 3.0f, playerHeight, EPSILON);
        assertEquals(-0.125f,
                SpriteMovementApplication.toClipCenter(0.0f, 0.0f, viewportWidth)
                        - playerWidth * 0.5f,
                EPSILON);
        assertEquals(-1.0f / 6.0f,
                SpriteMovementApplication.toClipCenter(0.0f, 0.0f, viewportHeight)
                        - playerHeight * 0.5f,
                EPSILON);
        assertEquals(0.5f,
                SpriteMovementApplication.toClipCenter(5.0f, 3.0f, viewportWidth),
                EPSILON);
        assertEquals(0.125f,
                SpriteMovementApplication.toClipSize(-1.0f, viewportWidth * 2.0f),
                EPSILON);
    }

    @Test
    void finishRenderEndsBatchAndPassAndSuppressesCleanupFailuresOnRenderFailure() {
        ArrayList<String> endOrder = new ArrayList<>();
        RuntimeException renderFailure = new IllegalStateException("render failed");
        RuntimeException batchFailure = new IllegalStateException("batch end failed");
        Error passFailure = new AssertionError("pass end failed");
        EndHandler batchHandler = new EndHandler("batch", endOrder, batchFailure);
        EndHandler passHandler = new EndHandler("pass", endOrder, passFailure);

        assertDoesNotThrow(() -> SpriteMovementApplication.finishRender(
                proxy(Batch2D.class, batchHandler),
                proxy(RenderPass.class, passHandler),
                true,
                renderFailure));

        assertEquals(1, batchHandler.endCount);
        assertEquals(1, passHandler.endCount);
        assertEquals("batch", endOrder.get(0));
        assertEquals("pass", endOrder.get(1));
        assertEquals(2, renderFailure.getSuppressed().length);
        assertSame(batchFailure, renderFailure.getSuppressed()[0]);
        assertSame(passFailure, renderFailure.getSuppressed()[1]);
    }

    @Test
    void finishRenderStillEndsPassAndRethrowsCleanupFailureWhenBatchEndFails() {
        ArrayList<String> endOrder = new ArrayList<>();
        RuntimeException batchFailure = new IllegalStateException("batch end failed");
        Error passFailure = new AssertionError("pass end failed");
        EndHandler batchHandler = new EndHandler("batch", endOrder, batchFailure);
        EndHandler passHandler = new EndHandler("pass", endOrder, passFailure);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> SpriteMovementApplication.finishRender(
                        proxy(Batch2D.class, batchHandler),
                        proxy(RenderPass.class, passHandler),
                        true,
                        null));

        assertSame(batchFailure, thrown);
        assertEquals(1, batchHandler.endCount);
        assertEquals(1, passHandler.endCount);
        assertEquals("batch", endOrder.get(0));
        assertEquals("pass", endOrder.get(1));
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(passFailure, thrown.getSuppressed()[0]);
    }

    @Test
    void finishRenderSkipsUnstartedBatchButStillEndsPass() {
        ArrayList<String> endOrder = new ArrayList<>();
        EndHandler batchHandler = new EndHandler("batch", endOrder, null);
        EndHandler passHandler = new EndHandler("pass", endOrder, null);

        SpriteMovementApplication.finishRender(
                proxy(Batch2D.class, batchHandler),
                proxy(RenderPass.class, passHandler),
                false,
                new IllegalStateException("batch begin failed"));

        assertEquals(0, batchHandler.endCount);
        assertEquals(1, passHandler.endCount);
        assertEquals("pass", endOrder.get(0));
    }

    private static final class MutableMovementInput implements MovementInput {
        float horizontal;
        float vertical;

        @Override
        public float horizontal() {
            return horizontal;
        }

        @Override
        public float vertical() {
            return vertical;
        }
    }

    private static final class EndHandler implements InvocationHandler {
        private final String name;
        private final ArrayList<String> endOrder;
        private final Throwable failure;
        private int endCount;

        EndHandler(String name, ArrayList<String> endOrder, Throwable failure) {
            this.name = name;
            this.endOrder = endOrder;
            this.failure = failure;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if ("end".equals(method.getName())) {
                endCount++;
                endOrder.add(name);
                throwUnchecked(failure);
            }
            return null;
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }
}
