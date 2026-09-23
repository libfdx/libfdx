package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Application-thread collection and residency scope. seal() fixes its total. Returned handles
 * are borrowed from this scope; retain() a handle to extend an independent consumer's lifetime.
 */
public final class ShaderPreparationScope implements Disposable {
    final ShaderPreparation owner;
    final String label;
    final List<PreparedShaderPass> members = new ArrayList<>();
    final FdxFuture<ShaderPreparationReport> completion;
    boolean sealed, submitted, disposed;

    ShaderPreparationScope(ShaderPreparation owner, String label) {
        this.owner = owner;
        this.label = label;
        completion = owner.newFuture();
    }

    /** Queues a requirement without compiling; duplicate members do not inflate the total. */
    public PreparedShaderPass include(ShaderProvider provider, ShaderRequest request) {
        owner.requireOpen();
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(request, "request");
        if (disposed || sealed) throw new FdxException("Preparation scope is sealed or disposed");
        for (PreparedShaderPass member : members) {
            if (member.entry.key.matches(provider, provider.revision(), request)) return member;
        }
        PreparedShaderPass handle = owner.request(provider, request, false);
        members.add(handle);
        return handle;
    }

    public ShaderPreparationScope seal() {
        owner.requireOpen();
        if (disposed) throw new FdxException("Preparation scope is disposed");
        sealed = true;
        return this;
    }

    /** Resolves a portable catalog against loaded content/current targets. Failures are reported
     * per recipe and do not abandon successful imports. No shader compilation occurs here. */
    public ShaderPreloadImportReport include(ShaderPreloadManifest manifest, ShaderPreloadResolver resolver) {
        owner.requireOpen();
        if (disposed || sealed) throw new FdxException("Preparation scope is sealed or disposed");
        Objects.requireNonNull(manifest); Objects.requireNonNull(resolver);
        List<ShaderPreloadImportReport.Item> results = new ArrayList<>();
        for (ShaderPreloadRecipe recipe : manifest.recipes()) {
            if (recipe.factory().equals("libfdx.requires-input")) {
                results.add(new ShaderPreloadImportReport.Item(recipe, ShaderPreloadResolver.Status.REQUIRES_INPUT,
                        recipe.parameters().getOrDefault("missing", "Register a stable recipe factory and its inputs")));
                continue;
            }
            try {
                ShaderPreloadResolver.Resolution resolution = Objects.requireNonNull(resolver.resolve(recipe));
                ShaderPreloadResolver.Status status = resolution.status();
                String message = resolution.message();
                if (status == ShaderPreloadResolver.Status.RESOLVED) {
                    PreparedShaderPass handle = include(resolution.provider(), resolution.request());
                    if (handle.state() == ShaderPreparationState.UNSUPPORTED) {
                        status = ShaderPreloadResolver.Status.UNSUPPORTED;
                        message = handle.failure() != null ? handle.failure().getMessage() : "Unsupported requirement";
                    }
                }
                results.add(new ShaderPreloadImportReport.Item(recipe, status, message));
            } catch (RuntimeException failure) {
                results.add(new ShaderPreloadImportReport.Item(recipe, ShaderPreloadResolver.Status.FAILED, failure.toString()));
            }
        }
        return new ShaderPreloadImportReport(results, manifest.diagnostics());
    }

    public String label() { return label; }
    public int totalCount() { owner.requireThread(); return members.size(); }
    public boolean isSealed() { return sealed; }

    public int readyCount() {
        return count(ShaderPreparationState.READY);
    }

    public int queuedCount() { return count(ShaderPreparationState.QUEUED); }
    public int preparingCount() { return count(ShaderPreparationState.PREPARING); }
    public int failedCount() { return count(ShaderPreparationState.FAILED); }
    public int unsupportedCount() { return count(ShaderPreparationState.UNSUPPORTED); }
    public int cancelledCount() { return count(ShaderPreparationState.CANCELLED); }

    private int count(ShaderPreparationState state) {
        owner.requireThread();
        int count = 0;
        for (int i = 0; i < members.size(); i++) if (members.get(i).state() == state) count++;
        return count;
    }

    boolean settled() {
        for (int i = 0; i < members.size(); i++) if (!members.get(i).state().terminal()) return false;
        return true;
    }

    void complete() {
        List<ShaderPreparationReport.Item> items = new ArrayList<>(members.size());
        for (PreparedShaderPass member : members) {
            items.add(new ShaderPreparationReport.Item(member.request(), member.state(), member.failure(), member.timings()));
        }
        completion.complete(new ShaderPreparationReport(label, items));
    }

    @Override
    public void dispose() {
        owner.requireThread();
        if (disposed) return;
        disposed = true;
        for (PreparedShaderPass member : members) member.dispose();
    }

    @Override
    public boolean isDisposed() { return disposed; }
}
