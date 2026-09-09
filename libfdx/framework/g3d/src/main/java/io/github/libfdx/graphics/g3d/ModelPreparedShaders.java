package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.runtime.PreparedShaderPass;
import io.github.libfdx.graphics.shader.runtime.ResolvedShaderPass;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOrigin;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationState;
import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.graphics.shader.runtime.ShaderSkippedDraws;
import io.github.libfdx.graphics.VertexLayout;

/** Batch-owned retained request cache. Ready selection uses structural metadata, not source
 * hashing, descriptor copies, futures or queued work. Level scopes independently pin entries. */
final class ModelPreparedShaders implements Disposable {
    private final ShaderPreparation service;
    private final ModelShaderPlan plan;
    private final Entry[] entries = new Entry[128];
    final ShaderSkippedDraws skipped = new ShaderSkippedDraws();
    private long clock;
    private boolean disposed;

    ModelPreparedShaders(ShaderPreparation service, ModelShaderPlan plan) {
        this.service = service;
        this.plan = plan;
        plan.requireDomain(service.device());
    }

    void beginFrame() { skipped.beginFrame(service.frameIndex()); }

    ResolvedShaderPass resolve(Renderable3D renderable, ShaderPassId pass, RenderTargetLayout target) {
        return resolve(renderable, pass, target, plan.provider(renderable));
    }

    /** Records an opaque batch renderer through the same bounded retained/capture path. */
    void unsupported(Renderable3D renderable, ShaderPassId pass, RenderTargetLayout target) {
        resolve(renderable, pass, target, plan.unavailableProvider());
    }

    private ResolvedShaderPass resolve(Renderable3D renderable, ShaderPassId pass, RenderTargetLayout target,
            ShaderProvider provider) {
        if (provider == null) { skipped.record(ShaderPreparationState.UNSUPPORTED, 1); return null; }
        VertexLayout layout = plan.layout(renderable);
        PrimitiveTopology topology = renderable.meshPart().primitiveTopology();
        String variant = plan.variant(renderable);
        long revision = provider.revision();
        int slot = -1;
        long oldest = Long.MAX_VALUE;
        for (int i = 0; i < entries.length; i++) {
            Entry entry = entries[i];
            if (entry == null) { if (oldest != Long.MIN_VALUE) { slot = i; oldest = Long.MIN_VALUE; } continue; }
            if (entry.provider == provider && entry.pass.equals(pass) && entry.target.equals(target)
                    && entry.layout.equals(layout) && entry.topology == topology && entry.variant.equals(variant)) {
                entry.lastUse = ++clock;
                if (revision != entry.revision) {
                    if (entry.current != entry.ready) entry.current.dispose();
                    entry.current = service.request(provider, plan.request(renderable, pass, target));
                    entry.origin = origin(entry, renderable);
                    if (entry.ready != null && !provider.canRenderPreparedRevision(entry.current.request(), entry.ready.readyPass())) {
                        entry.ready.dispose(); entry.ready = null;
                    }
                    entry.revision = revision;
                }
                return ready(entry, renderable);
            }
            if (entry.lastUse < oldest) { oldest = entry.lastUse; slot = i; }
        }
        if (entries[slot] != null) entries[slot].dispose();
        Entry entry = new Entry(provider, pass, target, layout, topology, variant, revision,
                service.request(provider, plan.request(renderable, pass, target)), ++clock);
        entries[slot] = entry;
        entry.origin = origin(entry, renderable);
        return ready(entry, renderable);
    }

    private ShaderPreparationOrigin origin(Entry entry, Renderable3D renderable) {
        return entry.provider == plan.unavailableProvider()
                ? new ShaderPreparationOrigin("ModelBatch", renderable.meshPart().mesh().id(), renderable.material().id(), "", null)
                : plan.origin(renderable, entry.current.request());
    }

    private ResolvedShaderPass ready(Entry entry, Renderable3D renderable) {
        ResolvedShaderPass result = entry.current.readyPass();
        if (result != null) {
            if (entry.ready != null && entry.ready != entry.current) entry.ready.dispose();
            entry.ready = entry.current;
        } else if (entry.ready != null) {
            result = entry.ready.readyPass();
        }
        if (result == null) skipped.record(entry.current.state(), 1);
        entry.current.recordDraws(entry.origin, renderable.meshPart().mesh().id(), renderable.material().id(), 1, result == null);
        return result;
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        for (int i = 0; i < entries.length; i++) {
            if (entries[i] != null) entries[i].dispose();
            entries[i] = null;
        }
    }
    @Override public boolean isDisposed() { return disposed; }

    private static final class Entry {
        final ShaderProvider provider;
        final ShaderPassId pass;
        final RenderTargetLayout target;
        final VertexLayout layout;
        final PrimitiveTopology topology;
        final String variant;
        long revision, lastUse;
        PreparedShaderPass current, ready;
        ShaderPreparationOrigin origin;
        Entry(ShaderProvider provider, ShaderPassId pass, RenderTargetLayout target, VertexLayout layout,
                PrimitiveTopology topology, String variant, long revision, PreparedShaderPass current, long lastUse) {
            this.provider = provider; this.pass = pass; this.target = target; this.layout = layout;
            this.topology = topology; this.variant = variant; this.revision = revision;
            this.current = current; this.lastUse = lastUse;
        }
        void dispose() {
            if (current != ready) current.dispose();
            if (ready != null) ready.dispose();
        }
    }
}
