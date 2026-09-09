package io.github.libfdx.graphics.shader.internal;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.FdxTask;
import java.util.function.Consumer;
import java.util.function.Function;

/** Internal composition on a borrowed provider executor, with rejection propagated as failure. */
public final class ShaderCompilationTasks {
    private ShaderCompilationTasks() { }

    public static <T> FdxFuture<T> submit(Consumer<Runnable> execute, FdxTask<T> work) {
        FdxFuture<T> result = FdxFuture.pending();
        try {
            execute.accept(() -> {
                T value;
                try { value = work.run(); }
                catch (Throwable error) { result.completeExceptionally(error); return; }
                result.complete(value);
            });
        } catch (Throwable error) { result.completeExceptionally(error); }
        return result;
    }

    public static <T, R> FdxFuture<R> then(FdxFuture<T> input, Consumer<Runnable> execute,
            Function<T, FdxFuture<R>> next) {
        FdxFuture<R> result = FdxFuture.pending();
        input.onFailure(result::completeExceptionally).onSuccess(value ->
                submit(execute, () -> next.apply(value)).onFailure(result::completeExceptionally)
                        .onSuccess(pending -> pending.onFailure(result::completeExceptionally).onSuccess(result::complete)));
        return result;
    }
}
