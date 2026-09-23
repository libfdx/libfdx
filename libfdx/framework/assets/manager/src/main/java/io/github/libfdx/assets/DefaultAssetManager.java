package io.github.libfdx.assets;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ObjectIterator;
import io.github.libfdx.collections.ObjectMap;
import io.github.libfdx.collections.ObjectQueue;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.FdxTask;
import io.github.libfdx.files.FileHandle;
import io.github.libfdx.files.FileSystem;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Application-thread-owned asset manager with a budgeted completion queue.
 * Construct and operate it on the application thread. Preparation workers only
 * enqueue results; finalization and managed load-result callbacks run in update.
 * Explicit unload/dispose operations deliver cancellation callbacks inline on
 * the application thread. Cleanup continues after errors and reports them after
 * all affected resources and dependencies have been released.
 *
 * <p>The file system and optional executor are borrowed. Without an executor,
 * CPU preparation runs cooperatively as update steps. Legacy loaders may still
 * execute inline in load(); use the context's preparation/finalization methods
 * to participate in scheduling. Late callbacks attached to completed futures
 * retain FdxFuture's immediate-callback behavior.</p>
 *
 * <p>Each path/type has one options configuration per manager. Conflicting
 * options or replacement loaders with live entries are rejected. Use separate
 * managers for separate graphics/resource domains. Dependencies are borrowed
 * and retained until the last direct request, lease, or parent releases them.
 * Scopes and leases belong to this manager; resource sharing never crosses
 * manager boundaries. Lease notifications share the update queue's budget.</p>
 *
 * @author xpenatan
 */
public final class DefaultAssetManager implements AssetManager {
    private final ObjectMap<Class<?>, AssetLoader<?>> loaders = new ObjectMap<Class<?>, AssetLoader<?>>();
    private final ObjectMap<Class<?>, Disposable> preparations = new ObjectMap<>();
    private final ObjectMap<String, Handle<?>> handles = new ObjectMap<String, Handle<?>>();
    private final Array<Handle<?>> handleValues = new Array<Handle<?>>();
    private final Array<Scope> scopes = new Array<Scope>(false, 0);
    private final Array<Lease<?>> leases = new Array<Lease<?>>(false, 0);
    private final ObjectQueue<QueueStep> updateTasks = new ObjectQueue<QueueStep>();
    private final ObjectQueue<QueueStep> deferredTasks = new ObjectQueue<QueueStep>();
    private final FileSystem files;
    private final AssetExecutor executor;
    private final LongSupplier clock;
    private final Thread applicationThread = Thread.currentThread();
    private volatile boolean disposed;
    private boolean updating;
    private int lastUpdateTaskCount;
    private long lastUpdateNanos;
    private long lastUpdateMaxTaskNanos;

    /** Creates a manager using cooperative preparation on the application thread. */
    public DefaultAssetManager(FileSystem files) {
        this(files, null, System::nanoTime);
    }

    /**
     * Creates a manager borrowing the file system and preparation executor.
     * @param files the non-null file system
     * @param executor the executor, or null for cooperative preparation
     */
    public DefaultAssetManager(FileSystem files, AssetExecutor executor) {
        this(files, executor, System::nanoTime);
    }

    DefaultAssetManager(FileSystem files, AssetExecutor executor, LongSupplier clock) {
        if (files == null || clock == null) {
            throw new FdxException("File system and clock cannot be null");
        }
        this.files = files;
        this.executor = executor;
        this.clock = clock;
    }

    /** {@inheritDoc} */
    @Override
    public AssetScope createScope() {
        checkActive();
        Scope scope = new Scope();
        scope.managerIndex = scopes.size();
        scopes.add(scope);
        return scope;
    }

    /** {@inheritDoc} */
    @Override
    public <T> AssetLease<T> acquire(AssetDescriptor<T> descriptor) {
        checkActive();
        return acquire(descriptor, null);
    }

    private <T> Lease<T> acquire(AssetDescriptor<T> descriptor, Scope scope) {
        Handle<T> handle = request(descriptor);
        Lease<T> lease = new Lease<T>(handle, scope);
        lease.managerIndex = leases.size();
        leases.add(lease);
        lease.handleIndex = handle.leases.size();
        handle.leases.add(lease);
        if (scope != null) {
            lease.scopeIndex = scope.leases.size();
            scope.leases.add(lease);
        }
        startLoad(handle);
        if (!handle.pending()) {
            lease.queueNotification();
        }
        return lease;
    }

    /** {@inheritDoc} */
    @Override
    public <T> AssetHandle<T> load(AssetDescriptor<T> descriptor) {
        checkActive();
        Handle<T> handle = request(descriptor);
        handle.directOwner = true;
        startLoad(handle);
        return handle;
    }

    /** Returns the load result; background execution requires a preparation executor. */
    @Override
    public <T> FdxFuture<T> loadAsync(AssetDescriptor<T> descriptor) {
        return load(descriptor).future();
    }

    /** {@inheritDoc} */
    @Override
    public boolean update() {
        return update(Integer.MAX_VALUE, Long.MAX_VALUE);
    }

    /** {@inheritDoc} */
    @Override
    public boolean update(int maxTasks, long maxNanos) {
        checkActive();
        if (maxTasks < 0 || maxNanos < 0L) {
            throw new FdxException("Asset update budgets cannot be negative");
        }
        if (updating) {
            throw new FdxException("Asset update cannot be called recursively");
        }
        updating = true;
        lastUpdateTaskCount = 0;
        lastUpdateMaxTaskNanos = 0L;
        long start = clock.getAsLong();
        try {
            while (!disposed && lastUpdateTaskCount < maxTasks && clock.getAsLong() - start < maxNanos) {
                QueueStep step;
                synchronized (updateTasks) {
                    step = updateTasks.pollFirst();
                }
                if (step == null) {
                    break;
                }
                long stepStart = clock.getAsLong();
                try {
                    if (!step.runStep()) {
                        // Retry backpressure only on a subsequent update, without
                        // blocking ready completions or spinning on a full executor.
                        deferredTasks.addLast(step);
                    }
                } finally {
                    lastUpdateTaskCount++;
                    lastUpdateMaxTaskNanos = Math.max(lastUpdateMaxTaskNanos, clock.getAsLong() - stepStart);
                }
            }
        } finally {
            synchronized (updateTasks) {
                QueueStep deferred;
                while ((deferred = deferredTasks.pollFirst()) != null) {
                    if (!disposed) {
                        updateTasks.addLast(deferred);
                    }
                }
            }
            lastUpdateNanos = Math.max(0L, clock.getAsLong() - start);
            updating = false;
        }
        return !hasPendingWork();
    }

    /** Number of queue steps attempted by the last update, including executor backpressure. */
    public int lastUpdateTaskCount() {
        return lastUpdateTaskCount;
    }

    /** Elapsed time spent in the last update, in nanoseconds. */
    public long lastUpdateNanos() {
        return lastUpdateNanos;
    }

    /** Longest non-preemptible queue step in the last update, in nanoseconds. */
    public long lastUpdateMaxTaskNanos() {
        return lastUpdateMaxTaskNanos;
    }

    /** {@inheritDoc} */
    @Override
    public <T> T get(String path, Class<T> type) {
        T asset = find(path, type);
        if (asset == null) {
            Handle<?> handle = handles.get(key(path, type));
            if (handle != null && handle.failure != null) {
                throw new FdxException("Asset failed to load: " + path + " as " + type.getName(), handle.failure);
            }
            throw new FdxException("Asset is not loaded: " + path + " as " + type.getName());
        }
        return asset;
    }

    /** {@inheritDoc} */
    @Override
    public <T> T find(String path, Class<T> type) {
        checkThread();
        Handle<?> handle = handles.get(key(path, type));
        Object asset = handle != null && handle.isLoaded() ? handle.asset : null;
        return type != null && type.isInstance(asset) ? type.cast(asset) : null;
    }

    /** {@inheritDoc} */
    @Override
    public void unload(String path) {
        checkActive();
        String normalized = path != null ? path.replace('\\', '/') : "";
        Array<Handle<?>> released = new Array<Handle<?>>();
        for (int i = 0; i < handleValues.size(); i++) {
            Handle<?> handle = handleValues.get(i);
            if (handle.descriptor.path().equals(normalized)) {
                handle.directOwner = false;
                released.add(handle);
            }
        }
        Throwable cleanupFailure = null;
        for (int i = 0; i < released.size(); i++) {
            try {
                releaseIfUnowned(released.get(i));
            } catch (Throwable error) {
                cleanupFailure = combine(cleanupFailure, error);
            }
        }
        rethrowCleanup(cleanupFailure);
    }

    /** {@inheritDoc} */
    @Override
    public void registerLoader(Class<?> type, AssetLoader<?> loader) {
        checkActive();
        if (type == null || loader == null || loader.type() != type) {
            throw new FdxException("Asset loader must match a non-null Java type");
        }
        if (loaders.get(type) != loader) {
            for (int i = 0; i < handleValues.size(); i++) {
                if (handleValues.get(i).descriptor.type() == type) {
                    throw new FdxException("Cannot replace a loader with live asset entries: " + type.getName());
                }
            }
        }
        loaders.put(type, loader);
    }

    /** Releases parents before dependencies, then disposes manager-owned CPU preparation resources.
     * Registered loaders and the executor remain borrowed. */
    @Override
    public void dispose() {
        checkThread();
        if (disposed) {
            return;
        }
        disposed = true;
        for (int i = 0; i < handleValues.size(); i++) {
            handleValues.get(i).directOwner = false;
        }
        for (int i = 0; i < scopes.size(); i++) {
            scopes.get(i).closed = true;
        }
        scopes.clear();
        Throwable cleanupFailure = null;
        while (!leases.isEmpty()) {
            try {
                leases.get(leases.size() - 1).dispose();
            } catch (Throwable error) {
                cleanupFailure = combine(cleanupFailure, error);
            }
        }
        while (!handleValues.isEmpty()) {
            Handle<?> root = null;
            for (int i = 0; i < handleValues.size(); i++) {
                if (handleValues.get(i).parents.isEmpty()) {
                    root = handleValues.get(i);
                    break;
                }
            }
            if (root == null) {
                throw new FdxException("Asset dependency graph contains a cycle during disposal");
            }
            try {
                releaseIfUnowned(root);
            } catch (Throwable error) {
                cleanupFailure = combine(cleanupFailure, error);
            }
        }
        loaders.clear();
        for (ObjectIterator<Disposable> it = preparations.values().iterator(); it.hasNext();) {
            Disposable resource = it.next();
            if (resource == null) continue; // A reentrant factory may be finishing during shutdown.
            try { resource.dispose(); }
            catch (Throwable error) { cleanupFailure = combine(cleanupFailure, error); }
        }
        preparations.clear();
        while (true) {
            QueueStep step;
            synchronized (updateTasks) {
                step = updateTasks.pollFirst();
            }
            if (step == null) {
                break;
            }
            try {
                step.discard();
            } catch (Throwable error) {
                cleanupFailure = combine(cleanupFailure, error);
            }
        }
        deferredTasks.clear();
        rethrowCleanup(cleanupFailure);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private <T> Handle<T> request(AssetDescriptor<T> descriptor) {
        if (descriptor == null) {
            throw new FdxException("Asset descriptor cannot be null");
        }
        String key = key(descriptor.path(), descriptor.type());
        @SuppressWarnings("unchecked")
        Handle<T> existing = (Handle<T>)handles.get(key);
        if (existing != null) {
            if (existing.descriptor.options().size() != descriptor.options().size()) {
                throw new FdxException("Conflicting asset options: " + key);
            }
            ObjectIterator<String> keys = descriptor.options().keys().iterator();
            while (keys.hasNext()) {
                String option = keys.next();
                if (!existing.descriptor.options().containsKey(option)
                        || !Objects.equals(existing.descriptor.options().get(option), descriptor.options().get(option))) {
                    throw new FdxException("Conflicting asset options: " + key + " option " + option);
                }
            }
            return existing;
        }
        Handle<T> created = new Handle<T>(descriptor, key);
        handles.put(key, created);
        handleValues.add(created);
        return created;
    }

    @SuppressWarnings("unchecked")
    private <T> void startLoad(Handle<T> handle) {
        if (handle.started || !handle.pending()) {
            return;
        }
        handle.started = true;
        handle.starting = true;
        handle.status = AssetStatus.LOADING;
        try {
            AssetLoader<T> loader = (AssetLoader<T>)loaders.get(handle.descriptor.type());
            if (loader == null) {
                throw new FdxException("No asset loader registered for " + handle.descriptor.type().getName());
            }
            FdxFuture<T> result = loader.load(handle.context, handle.descriptor);
            if (result == null) {
                throw new FdxException("Asset loader returned no future: " + handle.key);
            }
            result.onSuccess(value -> loaderSucceeded(handle, value))
                    .onFailure(error -> loaderFailed(handle, error));
        } catch (Throwable error) {
            loaderFailed(handle, error);
        } finally {
            handle.starting = false;
        }
    }

    private <T> void loaderSucceeded(Handle<T> handle, T value) {
        if (!handle.pending() || disposed) {
            disposeAsset(value);
            return;
        }
        if (handle.starting && !handle.context.managed && Thread.currentThread() == applicationThread) {
            acceptCandidate(handle, value);
        } else {
            // Keep ownership attached to this attempt even before the publication
            // step runs, so cancellation disposes the parent before its children.
            ValueStep<T> step = new ValueStep<T>(value, result -> {
                synchronized (updateTasks) {
                    handle.queuedResult = null;
                }
                acceptCandidate(handle, result);
            });
            boolean accepted;
            synchronized (updateTasks) {
                accepted = handle.pending() && !disposed;
                if (accepted) {
                    handle.queuedResult = step;
                    updateTasks.addLast(step);
                }
            }
            if (!accepted) {
                step.discard();
            }
        }
    }

    private void loaderFailed(Handle<?> handle, Throwable error) {
        if (!handle.pending() || disposed) {
            return;
        }
        if (handle.starting && !handle.context.managed && Thread.currentThread() == applicationThread) {
            fail(handle, error);
        } else {
            enqueue(() -> {
                fail(handle, error);
                return true;
            });
        }
    }

    private <T> void acceptCandidate(Handle<T> handle, T value) {
        if (!handle.pending()) {
            disposeAsset(value);
            return;
        }
        handle.context.discoveryClosed = true;
        handle.candidate = value;
        handle.candidateReady = true;
        publishIfReady(handle);
    }

    private <T> void publishIfReady(Handle<T> handle) {
        if (!handle.pending() || !handle.candidateReady || handle.pendingDependencies != 0) {
            return;
        }
        handle.asset = handle.candidate;
        handle.candidate = null;
        handle.candidateReady = false;
        handle.status = AssetStatus.LOADED;
        notifyParents(handle);
        notifyLeases(handle);
        handle.future.complete(handle.asset);
    }

    private void fail(Handle<?> handle, Throwable error) {
        if (!handle.pending()) {
            return;
        }
        handle.failure = error != null ? error : new FdxException("Asset load failed: " + handle.key);
        handle.status = AssetStatus.FAILED;
        notifyParents(handle);
        notifyLeases(handle);
        try {
            disposeCandidate(handle);
        } catch (Throwable cleanupError) {
            combine(handle.failure, cleanupError);
        }
        try {
            cancelTasks(handle, handle.failure);
        } catch (Throwable cleanupError) {
            combine(handle.failure, cleanupError);
        }
        try {
            releaseDependencies(handle);
        } catch (Throwable cleanupError) {
            combine(handle.failure, cleanupError);
        }
        handle.future.completeExceptionally(handle.failure);
    }

    private void notifyLeases(Handle<?> handle) {
        for (int i = 0; i < handle.leases.size(); i++) {
            handle.leases.get(i).queueNotification();
        }
    }

    private void notifyParents(Handle<?> child) {
        for (int i = 0; i < child.parents.size(); i++) {
            Edge edge = child.parents.get(i);
            if (!edge.resolved) {
                edge.resolved = true;
                enqueue(() -> {
                    Handle<?> parent = edge.parent;
                    if (parent.pending()) {
                        if (child.status == AssetStatus.LOADED) {
                            parent.pendingDependencies--;
                            if (parent.pendingDependencies == 0) {
                                for (int j = 0; j < parent.blocked.size(); j++) {
                                    enqueue(parent.blocked.get(j));
                                }
                                parent.blocked.clear();
                                publishIfReady(parent);
                            }
                        } else {
                            fail(parent, new FdxException("Dependency failed: " + parent.key + " -> " + child.key,
                                    child.failure));
                        }
                    }
                    return true;
                });
            }
        }
    }

    private boolean reaches(Handle<?> from, Handle<?> target, Array<Handle<?>> path) {
        if (path.contains(from, true)) {
            return false;
        }
        path.add(from);
        if (from == target) {
            return true;
        }
        for (int i = 0; i < from.dependencies.size(); i++) {
            if (reaches(from.dependencies.get(i).child, target, path)) {
                return true;
            }
        }
        path.removeIndex(path.size() - 1);
        return false;
    }

    private void releaseIfUnowned(Handle<?> handle) {
        if (handle.directOwner || !handle.leases.isEmpty() || !handle.parents.isEmpty()
                || handle.status == AssetStatus.UNLOADED) {
            return;
        }
        if (handles.get(handle.key) == handle) {
            handles.remove(handle.key);
            handleValues.removeValue(handle, true);
        }
        Object asset = handle.asset;
        handle.asset = null;
        handle.status = AssetStatus.UNLOADED;
        FdxException error = new FdxException("Asset released: " + handle.key);
        Throwable cleanupFailure = null;
        try {
            disposeAsset(asset);
        } catch (Throwable cleanupError) {
            cleanupFailure = cleanupError;
        }
        try {
            disposeCandidate(handle);
        } catch (Throwable cleanupError) {
            cleanupFailure = combine(cleanupFailure, cleanupError);
        }
        try {
            cancelTasks(handle, error);
        } catch (Throwable cleanupError) {
            cleanupFailure = combine(cleanupFailure, cleanupError);
        }
        try {
            releaseDependencies(handle);
        } catch (Throwable cleanupError) {
            cleanupFailure = combine(cleanupFailure, cleanupError);
        }
        try {
            handle.future.completeExceptionally(error);
        } catch (Throwable cleanupError) {
            cleanupFailure = combine(cleanupFailure, cleanupError);
        }
        rethrowCleanup(cleanupFailure);
    }

    private void disposeCandidate(Handle<?> handle) {
        ValueStep<?> queued;
        synchronized (updateTasks) {
            queued = handle.queuedResult;
            handle.queuedResult = null;
        }
        Object candidate = handle.candidate;
        handle.candidate = null;
        Throwable cleanupFailure = null;
        try {
            if (queued != null) { queued.discard(); }
        } catch (Throwable error) {
            cleanupFailure = error;
        }
        try {
            disposeAsset(candidate);
        } catch (Throwable error) {
            cleanupFailure = combine(cleanupFailure, error);
        }
        rethrowCleanup(cleanupFailure);
    }

    private void releaseDependencies(Handle<?> parent) {
        Throwable cleanupFailure = null;
        while (!parent.dependencies.isEmpty()) {
            Edge edge = parent.dependencies.removeIndex(parent.dependencies.size() - 1);
            edge.child.parents.removeValue(edge, true);
            try {
                releaseIfUnowned(edge.child);
            } catch (Throwable error) {
                cleanupFailure = combine(cleanupFailure, error);
            }
        }
        parent.pendingDependencies = 0;
        rethrowCleanup(cleanupFailure);
    }

    private void cancelTasks(Handle<?> handle, Throwable error) {
        handle.blocked.clear();
        for (int i = 0; i < handle.tasks.size(); i++) {
            handle.tasks.get(i).cancelled = true;
        }
        Throwable callbackFailure = null;
        while (!handle.tasks.isEmpty()) {
            PendingTask<?> task = handle.tasks.removeIndex(handle.tasks.size() - 1);
            try {
                task.future.completeExceptionally(error);
            } catch (Throwable failure) {
                callbackFailure = combine(callbackFailure, failure);
            }
        }
        rethrowCleanup(callbackFailure);
    }

    private static Throwable combine(Throwable first, Throwable next) {
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    private static void rethrowCleanup(Throwable error) {
        if (error instanceof RuntimeException) {
            throw (RuntimeException)error;
        }
        if (error instanceof Error) {
            throw (Error)error;
        }
        if (error != null) {
            throw new FdxException("Asset cleanup failed", error);
        }
    }

    private boolean enqueue(QueueStep step) {
        synchronized (updateTasks) {
            if (disposed) {
                return false;
            }
            updateTasks.addLast(step);
            return true;
        }
    }

    private <T> void enqueueValue(T value, Consumer<T> receiver) {
        ValueStep<T> step = new ValueStep<T>(value, receiver);
        if (!enqueue(step)) {
            step.discard();
        }
    }

    private boolean hasPendingWork() {
        synchronized (updateTasks) {
            if (!updateTasks.isEmpty()) {
                return true;
            }
        }
        for (int i = 0; i < handleValues.size(); i++) {
            if (handleValues.get(i).pending()) {
                return true;
            }
        }
        return false;
    }

    private String key(String path, Class<?> type) {
        return (path != null ? path.replace('\\', '/') : "") + "|" + (type != null ? type.getName() : "");
    }

    private void checkThread() {
        if (Thread.currentThread() != applicationThread) {
            throw new FdxException("Asset manager operations belong to the application thread");
        }
    }

    private void checkActive() {
        checkThread();
        if (disposed) {
            throw new FdxException("AssetManager is disposed");
        }
    }

    private static void disposeAsset(Object value) {
        if (value instanceof Disposable) {
            ((Disposable)value).dispose();
        }
    }

    private interface QueueStep {
        boolean runStep();

        default void discard() {
        }
    }

    private final class ValueStep<T> implements QueueStep {
        private T value;
        private final Consumer<T> receiver;
        private boolean consumed;

        ValueStep(T value, Consumer<T> receiver) {
            this.value = value;
            this.receiver = receiver;
        }

        @Override
        public boolean runStep() {
            if (consumed) {
                return true;
            }
            consumed = true;
            T delivered = value;
            value = null;
            receiver.accept(delivered);
            return true;
        }

        @Override
        public void discard() {
            if (consumed) {
                return;
            }
            consumed = true;
            T discarded = value;
            value = null;
            disposeAsset(discarded);
        }
    }

    private final class Context implements AssetLoadContext {
        final Handle<?> owner;
        boolean managed;
        boolean discoveryClosed;

        Context(Handle<?> owner) {
            this.owner = owner;
        }

        @Override
        public FileSystem files() {
            return files;
        }

        private void checkPending() {
            checkActive();
            if (!owner.pending()) {
                throw new FdxException("Asset load attempt is no longer pending: " + owner.key);
            }
            managed = true;
        }

        @Override
        public <T extends Disposable> T preparationResource(Class<T> type, Supplier<? extends T> factory) {
            checkPending();
            Objects.requireNonNull(type, "preparation type"); Objects.requireNonNull(factory, "preparation factory");
            if (preparations.containsKey(type)) {
                Disposable resource = preparations.get(type);
                if (resource == null) throw new FdxException("Recursive preparation resource creation: " + type.getName());
                return type.cast(resource);
            }
            preparations.put(type, null);
            try {
                T resource = Objects.requireNonNull(factory.get(), "preparation resource");
                try { checkPending(); }
                catch (RuntimeException | Error failure) {
                    try { resource.dispose(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                    throw failure;
                }
                preparations.put(type, resource);
                return resource;
            } catch (RuntimeException | Error failure) {
                preparations.remove(type);
                throw failure;
            }
        }

        @Override
        public <T> FdxFuture<T> dependency(AssetDescriptor<T> descriptor) {
            checkPending();
            if (discoveryClosed) {
                throw new FdxException("Declare dependencies before finalization: " + owner.key);
            }
            Handle<T> child = request(descriptor);
            for (int i = 0; i < owner.dependencies.size(); i++) {
                if (owner.dependencies.get(i).child == child) {
                    return child.future;
                }
            }
            Array<Handle<?>> cyclePath = new Array<Handle<?>>();
            if (reaches(child, owner, cyclePath)) {
                StringBuilder message = new StringBuilder("Asset dependency cycle: ").append(owner.key);
                for (int i = 0; i < cyclePath.size(); i++) {
                    message.append(" -> ").append(cyclePath.get(i).key);
                }
                throw new FdxException(message.toString());
            }
            Edge edge = new Edge(owner, child);
            owner.dependencies.add(edge);
            child.parents.add(edge);
            if (child.status == AssetStatus.LOADED) {
                edge.resolved = true;
            } else {
                owner.pendingDependencies++;
            }
            startLoad(child);
            if (child.status == AssetStatus.FAILED) {
                notifyParents(child);
            }
            return child.future;
        }

        @Override
        public <T> FdxFuture<T> completeOnUpdate(FdxTask<T> task) {
            checkPending();
            if (task == null) {
                throw new FdxException("Asset finalization task cannot be null");
            }
            discoveryClosed = true;
            FinalizationTask<T> pending = new FinalizationTask<T>(owner, task);
            if (owner.pendingDependencies == 0) {
                enqueue(pending);
            } else {
                owner.blocked.add(pending);
            }
            return pending.future;
        }

        @Override
        public <T> FdxFuture<T> async(FdxTask<T> task) {
            checkPending();
            if (task == null) {
                throw new FdxException("Asset preparation task cannot be null");
            }
            PreparationTask<T> pending = new PreparationTask<T>(owner, () -> FdxFuture.supply(task));
            enqueue(pending);
            return pending.future;
        }

        @Override
        public FdxFuture<Void> asyncSteps(FdxTask<Boolean> step) {
            checkPending();
            if (step == null) throw new FdxException("Preparation step cannot be null");
            if (executor != null) return AssetLoadContext.super.asyncSteps(step);
            FdxFuture<Void> result = FdxFuture.pending();
            scheduleStep(step, result);
            return result;
        }

        private void scheduleStep(FdxTask<Boolean> step, FdxFuture<Void> result) {
            try {
                async(step).onSuccess(done -> {
                    if (done) result.complete(null);
                    else scheduleStep(step, result);
                }).onFailure(result::completeExceptionally);
            } catch (RuntimeException | Error failure) {
                result.completeExceptionally(failure);
            }
        }

        @Override
        public FdxFuture<byte[]> readBytes(FileHandle file) {
            checkPending();
            if (file == null) {
                throw new FdxException("Asset file cannot be null");
            }
            PreparationTask<byte[]> pending = new PreparationTask<byte[]>(owner, file::readBytes);
            enqueue(pending);
            return pending.future;
        }

        @Override
        public <T> FdxFuture<T> asyncFuture(FdxTask<FdxFuture<T>> task) {
            checkPending();
            if (task == null) { throw new FdxException("Asset preparation task cannot be null"); }
            PreparationTask<T> pending = new PreparationTask<T>(owner, task);
            enqueue(pending);
            return pending.future;
        }
    }

    private abstract class PendingTask<T> implements QueueStep {
        final Handle<?> owner;
        final FdxFuture<T> future = FdxFuture.pending();
        volatile boolean cancelled;

        PendingTask(Handle<?> owner) {
            this.owner = owner;
            owner.tasks.add(this);
        }

        void complete(T value) {
            owner.tasks.removeValue(this, true);
            if (cancelled || !owner.pending()) {
                disposeAsset(value);
            } else {
                future.complete(value);
            }
        }

        void failTask(Throwable error) {
            owner.tasks.removeValue(this, true);
            if (!future.isDone()) {
                future.completeExceptionally(error);
            }
        }
    }

    private final class FinalizationTask<T> extends PendingTask<T> {
        final FdxTask<T> action;

        FinalizationTask(Handle<?> owner, FdxTask<T> action) {
            super(owner);
            this.action = action;
        }

        @Override
        public boolean runStep() {
            if (cancelled || !owner.pending()) {
                return true;
            }
            T value;
            try {
                value = action.run();
            } catch (Throwable error) {
                failTask(error);
                return true;
            }
            complete(value);
            return true;
        }
    }

    private final class PreparationTask<T> extends PendingTask<T> implements Runnable {
        final FdxTask<FdxFuture<T>> action;

        PreparationTask(Handle<?> owner, FdxTask<FdxFuture<T>> action) {
            super(owner);
            this.action = action;
        }

        @Override
        public boolean runStep() {
            if (cancelled || !owner.pending()) {
                return true;
            }
            if (executor == null) {
                run();
                return true;
            }
            try {
                return executor.submit(this);
            } catch (Throwable error) {
                failTask(error);
                return true;
            }
        }

        @Override
        public void run() {
            if (cancelled) {
                return;
            }
            FdxFuture<T> result;
            try {
                result = action.run();
                if (result == null) {
                    throw new FdxException("Asset preparation returned no future: " + owner.key);
                }
            } catch (Throwable error) {
                deliverFailure(error);
                return;
            }
            result.onSuccess(this::deliver).onFailure(this::deliverFailure);
        }

        private void deliver(T value) {
            if (cancelled) {
                disposeAsset(value);
            } else {
                enqueueValue(value, this::complete);
            }
        }

        private void deliverFailure(Throwable error) {
            if (!cancelled) {
                enqueue(() -> {
                    failTask(error);
                    return true;
                });
            }
        }
    }

    private final class Edge {
        final Handle<?> parent;
        final Handle<?> child;
        boolean resolved;

        Edge(Handle<?> parent, Handle<?> child) {
            this.parent = parent;
            this.child = child;
        }
    }

    private final class Scope implements AssetScope {
        final Array<Lease<?>> leases = new Array<Lease<?>>(false, 0);
        volatile boolean closed;
        int managerIndex;

        @Override
        public <T> AssetLease<T> load(AssetDescriptor<T> descriptor) {
            checkActive();
            if (closed) {
                throw new FdxException("Asset scope is disposed");
            }
            return acquire(descriptor, this);
        }

        @Override
        public void dispose() {
            checkThread();
            if (closed) {
                return;
            }
            closed = true;
            Scope moved = scopes.get(scopes.size() - 1);
            scopes.removeIndex(managerIndex);
            moved.managerIndex = managerIndex;
            Throwable cleanupFailure = null;
            while (!leases.isEmpty()) {
                try {
                    leases.get(leases.size() - 1).dispose();
                } catch (Throwable error) {
                    cleanupFailure = combine(cleanupFailure, error);
                }
            }
            rethrowCleanup(cleanupFailure);
        }

        @Override
        public boolean isDisposed() {
            return closed || disposed;
        }
    }

    private final class Lease<T> implements AssetLease<T>, QueueStep {
        final Handle<T> handle;
        final Scope scope;
        final FdxFuture<T> future = FdxFuture.pending();
        volatile boolean released;
        volatile AssetStatus status = AssetStatus.LOADING;
        boolean notificationQueued;
        int managerIndex;
        int handleIndex;
        int scopeIndex;

        Lease(Handle<T> handle, Scope scope) {
            this.handle = handle;
            this.scope = scope;
        }

        void queueNotification() {
            if (!notificationQueued && !isDisposed()) {
                notificationQueued = true;
                enqueue(this);
            }
        }

        @Override
        public boolean runStep() {
            if (!isDisposed() && !future.isDone()) {
                if (handle.status == AssetStatus.LOADED) {
                    status = AssetStatus.LOADED;
                    future.complete(handle.asset);
                } else if (handle.status == AssetStatus.FAILED) {
                    status = AssetStatus.FAILED;
                    future.completeExceptionally(handle.failure);
                }
            }
            return true;
        }

        @Override
        public void dispose() {
            checkThread();
            if (released) {
                return;
            }
            released = true;
            Lease<?> moved = leases.get(leases.size() - 1);
            leases.removeIndex(managerIndex);
            moved.managerIndex = managerIndex;
            moved = handle.leases.get(handle.leases.size() - 1);
            handle.leases.removeIndex(handleIndex);
            moved.handleIndex = handleIndex;
            if (scope != null) {
                moved = scope.leases.get(scope.leases.size() - 1);
                scope.leases.removeIndex(scopeIndex);
                moved.scopeIndex = scopeIndex;
            }
            Throwable cleanupFailure = null;
            try {
                releaseIfUnowned(handle);
            } catch (Throwable error) {
                cleanupFailure = error;
            }
            try {
                if (!future.isDone()) {
                    future.completeExceptionally(new FdxException("Asset lease released: " + handle.key));
                }
            } catch (Throwable error) {
                cleanupFailure = combine(cleanupFailure, error);
            }
            rethrowCleanup(cleanupFailure);
        }

        @Override
        public boolean isDisposed() { return released || disposed || scope != null && scope.closed; }
        @Override
        public AssetDescriptor<T> descriptor() { return handle.descriptor; }
        @Override
        public AssetStatus status() { return isDisposed() ? AssetStatus.UNLOADED : status; }
        @Override
        public boolean isLoaded() { return !isDisposed() && status == AssetStatus.LOADED; }
        @Override
        public T asset() { return isLoaded() ? handle.asset : null; }
        @Override
        public FdxFuture<T> future() { return future; }
    }

    private final class Handle<T> implements AssetHandle<T> {
        final AssetDescriptor<T> descriptor;
        final String key;
        final FdxFuture<T> future = FdxFuture.pending();
        final Context context;
        final Array<Edge> dependencies = new Array<Edge>(0);
        final Array<Edge> parents = new Array<Edge>(0);
        final Array<Lease<T>> leases = new Array<Lease<T>>(false, 0);
        final Array<PendingTask<?>> tasks = new Array<PendingTask<?>>(0);
        final Array<QueueStep> blocked = new Array<QueueStep>(0);
        volatile AssetStatus status = AssetStatus.QUEUED;
        volatile T asset;
        boolean directOwner;
        boolean started;
        boolean starting;
        int pendingDependencies;
        Throwable failure;
        T candidate;
        ValueStep<?> queuedResult;
        boolean candidateReady;

        Handle(AssetDescriptor<T> descriptor, String key) {
            this.descriptor = descriptor;
            this.key = key;
            context = new Context(this);
        }

        boolean pending() {
            return status == AssetStatus.QUEUED || status == AssetStatus.LOADING;
        }

        @Override
        public AssetDescriptor<T> descriptor() { return descriptor; }
        @Override
        public AssetStatus status() { return status; }
        @Override
        public boolean isLoaded() { return status == AssetStatus.LOADED; }
        @Override
        public T asset() { return asset; }
        @Override
        public FdxFuture<T> future() { return future; }
    }
}
