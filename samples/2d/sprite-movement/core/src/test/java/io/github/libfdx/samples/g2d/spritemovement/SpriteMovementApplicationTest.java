package io.github.libfdx.samples.g2d.spritemovement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.application.ApplicationAdapter;
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
    void applicationUsesPlainApplicationLifecycleAndFixedLevelState() {
        assertTrue(ApplicationAdapter.class.isAssignableFrom(SpriteMovementApplication.class));

        SpriteMovementState state = new SpriteMovementState();
        assertEquals(0.0f, state.playerX(), EPSILON);
        assertEquals(0.0f, state.playerY(), EPSILON);
        assertEquals(4, state.wallCount());
        assertWall(state.wallAt(0), -3.0f, 0.0f, 0.5f, 4.0f);
        assertWall(state.wallAt(1), 3.0f, 0.0f, 0.5f, 4.0f);
        assertWall(state.wallAt(2), 0.0f, -2.0f, 6.5f, 0.5f);
        assertWall(state.wallAt(3), 0.0f, 2.0f, 6.5f, 0.5f);
    }

    @Test
    void directUpdateMovesPlayerAndNormalizesDiagonalInput() {
        MutableMovementInput input = new MutableMovementInput();
        SpriteMovementState state = new SpriteMovementState();

        input.horizontal = 1.0f;
        state.update(input, 0.5f);
        assertEquals(1.5f, state.playerX(), EPSILON);
        assertEquals(0.0f, state.playerY(), EPSILON);

        state.reset();
        input.vertical = 1.0f;
        state.update(input, 0.5f);
        float diagonalDistance = 3.0f * 0.5f * 0.70710677f;
        assertEquals(diagonalDistance, state.playerX(), EPSILON);
        assertEquals(diagonalDistance, state.playerY(), EPSILON);
    }

    @Test
    void applicationAssetsAreCheckedIn() {
        assertTrue(Files.isRegularFile(Path.of("assets", SpriteMovementApplication.PLAYER_SPRITE)));
        assertTrue(Files.isRegularFile(Path.of("assets", SpriteMovementApplication.WALL_TILE)));
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
    void runtimeRepeatsWallTextureAtHalfWorldUnitIntervals() {
        assertEquals(1, SpriteMovementApplication.wallTileCount(0.0f));
        assertEquals(1, SpriteMovementApplication.wallTileCount(0.5f));
        assertEquals(2, SpriteMovementApplication.wallTileCount(0.51f));
        assertEquals(8, SpriteMovementApplication.wallTileCount(4.0f));
        assertEquals(13, SpriteMovementApplication.wallTileCount(6.5f));
        assertEquals(8, SpriteMovementApplication.wallTileCount(-4.0f));
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

    private static void assertWall(
            SpriteMovementState.Wall wall,
            float x,
            float y,
            float width,
            float height) {
        assertEquals(x, wall.x, EPSILON);
        assertEquals(y, wall.y, EPSILON);
        assertEquals(width, wall.width, EPSILON);
        assertEquals(height, wall.height, EPSILON);
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
