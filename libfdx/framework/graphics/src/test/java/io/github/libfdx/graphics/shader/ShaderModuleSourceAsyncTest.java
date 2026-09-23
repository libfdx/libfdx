package io.github.libfdx.graphics.shader;

import io.github.libfdx.core.FdxFuture;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShaderModuleSourceAsyncTest {
    @Test
    void deferredAsyncSourceWaitsAndCopiesOnThePreparationExecutor() {
        var pending = FdxFuture.<ShaderModuleDescriptor>pending();
        var source = ShaderModuleSource.deferred("vertexMain","fragmentMain",
                () -> { throw new AssertionError("Synchronous generator invoked"); }, execute -> pending);
        ArrayDeque<Runnable> work = new ArrayDeque<>();
        var result = source.generateAsync(work::add);
        assertFalse(result.isDone());
        var descriptor = ShaderModuleDescriptor.wgsl("generated","source");
        pending.complete(descriptor);
        assertFalse(result.isDone());
        while (!work.isEmpty()) work.remove().run();
        assertEquals("source",result.get().wgslSource());
        assertNotSame(descriptor,result.get());
    }
    @Test
    void asyncFailureAndCancelledExecutorDoNotInvokeSynchronousFallback() {
        var pending = FdxFuture.<ShaderModuleDescriptor>pending();
        var source = ShaderModuleSource.deferred("v","f", () -> { throw new AssertionError(); }, execute -> pending);
        var failure = source.generateAsync(Runnable::run);
        pending.completeExceptionally(new IllegalStateException("source failed"));
        assertTrue(failure.isFailed());
        var completed = ShaderModuleSource.deferred("v","f", () -> { throw new AssertionError(); },
                execute -> FdxFuture.completed(ShaderModuleDescriptor.wgsl("label","wgsl")));
        assertTrue(completed.generateAsync(work -> { throw new java.util.concurrent.CancellationException(); }).isFailed());
    }
}
