package io.github.libfdx.samples.g2d.spritemovement.render;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.g2d.Batch2D;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

final class SpriteMovementRenderSystemTest {
    @Test
    void finishRenderEndsBatchAndPassAndSuppressesCleanupFailuresOnRenderFailure() {
        ArrayList<String> endOrder = new ArrayList<>();
        RuntimeException renderFailure = new IllegalStateException("render failed");
        RuntimeException batchFailure = new IllegalStateException("batch end failed");
        Error passFailure = new AssertionError("pass end failed");
        EndHandler batchHandler = new EndHandler("batch", endOrder, batchFailure);
        EndHandler passHandler = new EndHandler("pass", endOrder, passFailure);

        assertDoesNotThrow(() -> SpriteMovementRenderSystem.finishRender(
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
                () -> SpriteMovementRenderSystem.finishRender(
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

        SpriteMovementRenderSystem.finishRender(
                proxy(Batch2D.class, batchHandler),
                proxy(RenderPass.class, passHandler),
                false,
                new IllegalStateException("batch begin failed"));

        assertEquals(0, batchHandler.endCount);
        assertEquals(1, passHandler.endCount);
        assertEquals("pass", endOrder.get(0));
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
