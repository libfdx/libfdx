package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

/**
 * Immutable color/resolve attachment and load/store operations.
 */
public final class RenderPassColorAttachment {
    private final TextureView view;
    private final TextureView resolveView;
    private final LoadOp loadOp;
    private final StoreOp storeOp;

    private RenderPassColorAttachment(TextureView view, TextureView resolveView,
            LoadOp loadOp, StoreOp storeOp) {
        if (view == null) {
            throw new FdxException("Render pass color attachment view cannot be null");
        }
        if (!view.format().isColor()) {
            throw new FdxException("Render pass color attachment must use a color format");
        }
        if (resolveView != null && (resolveView.format() != view.format()
                || resolveView.sampleCount() != 1 || view.sampleCount() <= 1)) {
            throw new FdxException("Render pass resolve attachment is incompatible with its color attachment");
        }
        this.view = view;
        this.resolveView = resolveView;
        this.loadOp = loadOp != null ? loadOp : LoadOp.load();
        this.storeOp = storeOp != null ? storeOp : StoreOp.store();
    }

    public static RenderPassColorAttachment of(TextureView view,
            LoadOp loadOp, StoreOp storeOp) {
        return new RenderPassColorAttachment(view, null, loadOp, storeOp);
    }

    public static RenderPassColorAttachment resolve(TextureView view,
            TextureView resolveView, LoadOp loadOp, StoreOp storeOp) {
        return new RenderPassColorAttachment(view, resolveView, loadOp, storeOp);
    }

    public TextureView view() {
        return view;
    }

    public TextureView resolveView() {
        return resolveView;
    }

    public LoadOp loadOp() {
        return loadOp;
    }

    public StoreOp storeOp() {
        return storeOp;
    }
}
