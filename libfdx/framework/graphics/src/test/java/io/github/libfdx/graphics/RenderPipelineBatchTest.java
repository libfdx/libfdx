package io.github.libfdx.graphics;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RenderPipelineBatchTest {
    @Test
    void returnsPipelinesInOrderWithoutDisposingSuccessfulResults() {
        Fixture fixture = new Fixture();
        var first = new RenderPipelineDescriptor();
        var second = new RenderPipelineDescriptor();
        RenderPipeline[] result = fixture.device.createRenderPipelines(first, second);
        assertEquals(List.of(first, second), fixture.descriptors);
        assertArrayEquals(fixture.created.toArray(RenderPipeline[]::new), result);
        assertEquals(List.of(), fixture.disposed);
        assertEquals(0, fixture.device.createRenderPipelines().length);
    }

    @Test
    void creationFailureDisposesPriorResultsAndPreservesOriginalError() {
        Fixture fixture = new Fixture();
        fixture.failAt = 3;
        fixture.failRelease = true;
        assertSame(fixture.creationFailure, assertThrows(IllegalArgumentException.class, () ->
                fixture.device.createRenderPipelines(new RenderPipelineDescriptor(),
                        new RenderPipelineDescriptor(), new RenderPipelineDescriptor())));
        assertEquals(List.of(2, 1), fixture.disposed);
        assertEquals(1, fixture.creationFailure.getSuppressed().length);
    }

    @Test
    void rejectsNullDescriptorsBeforeCreatingResources() {
        Fixture fixture = new Fixture();
        assertThrows(NullPointerException.class, () -> fixture.device.createRenderPipelines((RenderPipelineDescriptor[]) null));
        assertThrows(NullPointerException.class, () -> fixture.device.createRenderPipelines(new RenderPipelineDescriptor(), null));
        assertEquals(List.of(), fixture.created);
    }

    private static final class Fixture {
        final List<RenderPipelineDescriptor> descriptors = new ArrayList<>();
        final List<RenderPipeline> created = new ArrayList<>();
        final List<Integer> disposed = new ArrayList<>();
        final IllegalArgumentException creationFailure = new IllegalArgumentException("pipeline failed");
        int failAt;
        boolean failRelease;
        final GraphicsDevice device = (GraphicsDevice) Proxy.newProxyInstance(GraphicsDevice.class.getClassLoader(),
                new Class<?>[] {GraphicsDevice.class}, (proxy, method, args) -> {
                    if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                    if (!method.getName().equals("createRenderPipeline")) throw new AssertionError(method);
                    int index = created.size() + 1;
                    if (index == failAt) throw creationFailure;
                    descriptors.add((RenderPipelineDescriptor) args[0]);
                    RenderPipeline pipeline = (RenderPipeline) Proxy.newProxyInstance(RenderPipeline.class.getClassLoader(),
                            new Class<?>[] {RenderPipeline.class}, (p, m, a) -> {
                                if (!m.getName().equals("dispose")) throw new AssertionError(m);
                                disposed.add(index);
                                if (failRelease && index == 2) throw new IllegalStateException("release failed");
                                return null;
                            });
                    created.add(pipeline);
                    return pipeline;
                });
    }
}
