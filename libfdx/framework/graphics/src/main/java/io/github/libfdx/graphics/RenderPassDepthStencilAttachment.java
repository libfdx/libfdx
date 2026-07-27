package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

/**
 * Immutable explicit depth/stencil attachment operations.
 */
public final class RenderPassDepthStencilAttachment {
    private final TextureView view;
    private final LoadOp depthLoadOp;
    private final StoreOp depthStoreOp;
    private final LoadOp stencilLoadOp;
    private final StoreOp stencilStoreOp;

    private RenderPassDepthStencilAttachment(TextureView view,
            LoadOp depthLoadOp, StoreOp depthStoreOp,
            LoadOp stencilLoadOp, StoreOp stencilStoreOp) {
        if (view == null || !view.format().isDepthStencil()) {
            throw new FdxException("Depth/stencil attachment requires a depth/stencil texture view");
        }
        this.view = view;
        this.depthLoadOp = depthLoadOp != null ? depthLoadOp : LoadOp.load();
        this.depthStoreOp = depthStoreOp != null ? depthStoreOp : StoreOp.store();
        this.stencilLoadOp = stencilLoadOp != null ? stencilLoadOp : LoadOp.load();
        this.stencilStoreOp = stencilStoreOp != null ? stencilStoreOp : StoreOp.store();
    }

    public static RenderPassDepthStencilAttachment of(TextureView view,
            LoadOp depthLoadOp, StoreOp depthStoreOp,
            LoadOp stencilLoadOp, StoreOp stencilStoreOp) {
        return new RenderPassDepthStencilAttachment(view, depthLoadOp, depthStoreOp,
                stencilLoadOp, stencilStoreOp);
    }

    public TextureView view() {
        return view;
    }

    public LoadOp depthLoadOp() {
        return depthLoadOp;
    }

    public StoreOp depthStoreOp() {
        return depthStoreOp;
    }

    public LoadOp stencilLoadOp() {
        return stencilLoadOp;
    }

    public StoreOp stencilStoreOp() {
        return stencilStoreOp;
    }
}
