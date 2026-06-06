package io.github.libfdx.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class FdxFuture<T> {
    private final List<Consumer<T>> successCallbacks = new ArrayList<Consumer<T>>();
    private final List<Consumer<Throwable>> failureCallbacks = new ArrayList<Consumer<Throwable>>();
    private boolean done;
    private T value;
    private Throwable error;

    private FdxFuture() {
    }

    public static <T> FdxFuture<T> pending() {
        return new FdxFuture<T>();
    }

    public static <T> FdxFuture<T> completed(T value) {
        FdxFuture<T> future = new FdxFuture<T>();
        future.complete(value);
        return future;
    }

    public static <T> FdxFuture<T> failed(Throwable error) {
        FdxFuture<T> future = new FdxFuture<T>();
        future.completeExceptionally(error != null ? error : new FdxException("Future failed"));
        return future;
    }

    public static <T> FdxFuture<T> supply(FdxTask<T> task) {
        if (task == null) {
            throw new FdxException("Future task cannot be null");
        }
        try {
            return completed(task.run());
        } catch (Throwable error) {
            return failed(error);
        }
    }

    public FdxFuture<T> onSuccess(Consumer<T> callback) {
        if (callback == null) {
            return this;
        }
        T callbackValue = null;
        boolean callNow = false;
        synchronized (this) {
            if (!done) {
                successCallbacks.add(callback);
                return this;
            }
            if (error == null) {
                callbackValue = value;
                callNow = true;
            }
        }
        if (callNow) {
            callback.accept(callbackValue);
        }
        return this;
    }

    public FdxFuture<T> onFailure(Consumer<Throwable> callback) {
        if (callback == null) {
            return this;
        }
        Throwable callbackError = null;
        boolean callNow = false;
        synchronized (this) {
            if (!done) {
                failureCallbacks.add(callback);
                return this;
            }
            if (error != null) {
                callbackError = error;
                callNow = true;
            }
        }
        if (callNow) {
            callback.accept(callbackError);
        }
        return this;
    }

    public synchronized boolean isDone() {
        return done;
    }

    public synchronized boolean isFailed() {
        return done && error != null;
    }

    public T join() {
        return get();
    }

    public synchronized T get() {
        if (!done) {
            throw new FdxException("Future is not complete");
        }
        if (error != null) {
            if (error instanceof RuntimeException) {
                throw (RuntimeException) error;
            }
            throw new FdxException("Future failed", error);
        }
        return value;
    }

    public void complete(T value) {
        List<Consumer<T>> callbacks;
        synchronized (this) {
            if (done) {
                return;
            }
            this.value = value;
            done = true;
            callbacks = new ArrayList<Consumer<T>>(successCallbacks);
            successCallbacks.clear();
            failureCallbacks.clear();
        }
        for (int i = 0; i < callbacks.size(); i++) {
            callbacks.get(i).accept(value);
        }
    }

    public void completeExceptionally(Throwable error) {
        Throwable actualError = error != null ? error : new FdxException("Future failed");
        List<Consumer<Throwable>> callbacks;
        synchronized (this) {
            if (done) {
                return;
            }
            this.error = actualError;
            done = true;
            callbacks = new ArrayList<Consumer<Throwable>>(failureCallbacks);
            successCallbacks.clear();
            failureCallbacks.clear();
        }
        for (int i = 0; i < callbacks.size(); i++) {
            callbacks.get(i).accept(actualError);
        }
    }
}
