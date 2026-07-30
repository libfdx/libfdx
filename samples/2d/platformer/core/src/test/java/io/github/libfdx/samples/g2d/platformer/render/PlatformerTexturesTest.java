package io.github.libfdx.samples.g2d.platformer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.libfdx.graphics.Texture;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

final class PlatformerTexturesTest {
    @Test
    void cleanupAttemptsEveryTextureAndAggregatesFailures() {
        RuntimeException firstFailure = new IllegalStateException("first dispose failed");
        Error secondFailure = new AssertionError("second dispose failed");
        DisposeHandler first = new DisposeHandler(firstFailure);
        DisposeHandler second = new DisposeHandler(secondFailure);
        Texture[] textures = {
                proxy(first),
                proxy(second)
        };

        Throwable result = PlatformerTextures.disposeTextures(textures, null);

        assertSame(firstFailure, result);
        assertEquals(1, first.disposeCount);
        assertEquals(1, second.disposeCount);
        assertNull(textures[0]);
        assertNull(textures[1]);
        assertEquals(1, result.getSuppressed().length);
        assertSame(secondFailure, result.getSuppressed()[0]);
    }

    private static Texture proxy(InvocationHandler handler) {
        return (Texture) Proxy.newProxyInstance(
                Texture.class.getClassLoader(),
                new Class<?>[] {Texture.class},
                handler);
    }

    private static final class DisposeHandler implements InvocationHandler {
        private final Throwable failure;
        private int disposeCount;

        private DisposeHandler(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if ("dispose".equals(method.getName())) {
                disposeCount++;
                if (failure instanceof RuntimeException runtimeFailure) {
                    throw runtimeFailure;
                }
                if (failure instanceof Error error) {
                    throw error;
                }
            }
            if ("isDisposed".equals(method.getName())) {
                return false;
            }
            return null;
        }
    }
}
