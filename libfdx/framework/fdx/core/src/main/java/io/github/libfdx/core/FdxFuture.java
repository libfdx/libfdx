package io.github.libfdx.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Represents a fdx future.
 *
 * @param <T> the value type
 *
 * @author xpenatan
 */
public final class FdxFuture<T> {
    private final List<Consumer<T>> successCallbacks = new ArrayList<Consumer<T>>();
    private final List<Consumer<Throwable>> failureCallbacks = new ArrayList<Consumer<Throwable>>();
    private boolean done;
    private boolean dispatchingCallbacks;
    private T value;
    private Throwable error;

    private FdxFuture() {
    }

    /**
     * Returns the pending.
     *
     * @param <T> the value type
     * @return the pending
     */
    public static <T> FdxFuture<T> pending() {
        return new FdxFuture<T>();
    }

    /**
     * Runs the completed step.
     *
     * @param <T> the value type
     * @param value the value
     * @return the completed
     */
    public static <T> FdxFuture<T> completed(T value) {
        FdxFuture<T> future = new FdxFuture<T>();
        future.complete(value);
        return future;
    }

    /**
     * Runs the failed step.
     *
     * @param <T> the value type
     * @param error the error
     * @return the failed
     */
    public static <T> FdxFuture<T> failed(Throwable error) {
        FdxFuture<T> future = new FdxFuture<T>();
        future.completeExceptionally(error != null ? error : new FdxException("Future failed"));
        return future;
    }

    /**
     * Runs the supply step.
     *
     * @param <T> the value type
     * @param task the task
     * @return the supply
     */
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

    /**
     * Handles the success event.
     *
     * @param callback the callback to invoke
     * @return the on success
     */
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
                if (dispatchingCallbacks) {
                    successCallbacks.add(callback);
                    return this;
                }
                callbackValue = value;
                callNow = true;
            }
        }
        if (callNow) {
            callback.accept(callbackValue);
        }
        return this;
    }

    /**
     * Handles the failure event.
     *
     * @param callback the callback to invoke
     * @return the on failure
     */
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
                if (dispatchingCallbacks) {
                    failureCallbacks.add(callback);
                    return this;
                }
                callbackError = error;
                callNow = true;
            }
        }
        if (callNow) {
            callback.accept(callbackError);
        }
        return this;
    }

    /**
     * Returns whether done is enabled or true.
     *
     * @return true if done is enabled or true; false otherwise
     */
    public synchronized boolean isDone() {
        return done;
    }

    /**
     * Returns whether failed is enabled or true.
     *
     * @return true if failed is enabled or true; false otherwise
     */
    public synchronized boolean isFailed() {
        return done && error != null;
    }

    /**
     * Returns the join.
     *
     * @return the join
     */
    public T join() {
        return get();
    }

    /**
     * Returns the get.
     *
     * @return the get
     */
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

    /**
     * Runs the complete step.
     *
     * @param value the value
     */
    public void complete(T value) {
        List<Consumer<T>> callbacks;
        synchronized (this) {
            if (done) {
                return;
            }
            this.value = value;
            done = true;
            dispatchingCallbacks = true;
            callbacks = takeSuccessCallbacks();
            if (callbacks == null) {
                dispatchingCallbacks = false;
            }
            failureCallbacks.clear();
        }
        Throwable callbackFailure = null;
        try {
            while (callbacks != null) {
                callbackFailure = invokeCallbacks(callbacks, value, callbackFailure);
                synchronized (this) {
                    callbacks = takeSuccessCallbacks();
                    if (callbacks == null) {
                        dispatchingCallbacks = false;
                    }
                }
            }
        } finally {
            synchronized (this) {
                dispatchingCallbacks = false;
            }
        }
        rethrowCallbackFailure(callbackFailure);
    }

    /**
     * Runs the complete exceptionally step.
     *
     * @param error the error
     */
    public void completeExceptionally(Throwable error) {
        Throwable actualError = error != null ? error : new FdxException("Future failed");
        List<Consumer<Throwable>> callbacks;
        synchronized (this) {
            if (done) {
                return;
            }
            this.error = actualError;
            done = true;
            dispatchingCallbacks = true;
            callbacks = takeFailureCallbacks();
            if (callbacks == null) {
                dispatchingCallbacks = false;
            }
            successCallbacks.clear();
        }
        Throwable callbackFailure = null;
        try {
            while (callbacks != null) {
                callbackFailure = invokeCallbacks(callbacks, actualError, callbackFailure);
                synchronized (this) {
                    callbacks = takeFailureCallbacks();
                    if (callbacks == null) {
                        dispatchingCallbacks = false;
                    }
                }
            }
        } finally {
            synchronized (this) {
                dispatchingCallbacks = false;
            }
        }
        rethrowCallbackFailure(callbackFailure);
    }

    private List<Consumer<T>> takeSuccessCallbacks() {
        if (successCallbacks.isEmpty()) {
            return null;
        }
        List<Consumer<T>> callbacks = new ArrayList<Consumer<T>>(successCallbacks);
        successCallbacks.clear();
        return callbacks;
    }

    private List<Consumer<Throwable>> takeFailureCallbacks() {
        if (failureCallbacks.isEmpty()) {
            return null;
        }
        List<Consumer<Throwable>> callbacks = new ArrayList<Consumer<Throwable>>(failureCallbacks);
        failureCallbacks.clear();
        return callbacks;
    }

    private static <V> Throwable invokeCallbacks(List<Consumer<V>> callbacks, V callbackValue,
            Throwable firstFailure) {
        Throwable failure = firstFailure;
        for (int i = 0; i < callbacks.size(); i++) {
            try {
                callbacks.get(i).accept(callbackValue);
            } catch (Throwable callbackFailure) {
                if (failure == null) {
                    failure = callbackFailure;
                } else if (failure != callbackFailure) {
                    failure.addSuppressed(callbackFailure);
                }
            }
        }
        return failure;
    }

    private static void rethrowCallbackFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new FdxException("Future callback failed", failure);
    }
}
