package io.github.libfdx.backend.web;

import io.github.libfdx.graphics.meshoptimizer.ModelLodExecutor;
import io.github.libfdx.graphics.meshoptimizer.ModelLodInput;
import io.github.libfdx.graphics.meshoptimizer.ModelLodJob;
import io.github.libfdx.graphics.meshoptimizer.ModelLodSettings;

/** Application-owned browser worker pool. One admitted model per slot, lazy worker startup,
 * termination on cancellation. No cooperative or caller-runs optimizer fallback. The browser
 * must permit blob workers; failures are reported through the job and preserve the base model. */
public final class WebModelLodExecutor implements ModelLodExecutor {
    private final WebModelLodSlot[] slots;
    private boolean disposed;

    public WebModelLodExecutor(int workers) {
        if(workers < 1) throw new IllegalArgumentException("LOD workers must be positive");
        slots = new WebModelLodSlot[workers];
        for(int i = 0; i < workers; i++) slots[i] = new WebModelLodSlot();
    }
    @Override public int workers() { return slots.length; }
    @Override public void update() { for(WebModelLodSlot slot : slots) slot.update(); }
    @Override public boolean hasCapacity() {
        if(disposed) return false;
        update();
        for(WebModelLodSlot slot : slots) if(slot.available()) return true;
        return false;
    }
    @Override public ModelLodJob trySubmit(ModelLodInput input, ModelLodSettings settings) {
        if(disposed) throw new IllegalStateException("LOD executor is disposed");
        update();
        for(WebModelLodSlot slot : slots) if(slot.available()) {
            ModelLodJob job = new ModelLodJob(input, settings);
            slot.submit(job);
            return job;
        }
        return null;
    }
    @Override public boolean isDisposed() { return disposed; }
    @Override public void dispose() {
        if(disposed) return;
        disposed = true;
        for(WebModelLodSlot slot : slots) slot.dispose();
    }
}
