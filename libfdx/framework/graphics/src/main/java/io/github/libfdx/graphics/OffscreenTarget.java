package io.github.libfdx.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.ClipDepthRange;

/**
 * Application-owned resizable color/optional depth target, confined to the graphics thread.
 * Borrows the device. Allocation failure leaves the prior target valid. End all passes using
 * the target before resize/disposal; old textures/views become invalid after either operation.
 * Sample color only after the writing pass ends and resolves. No borrowed frame/pass is retained.
 */
public final class OffscreenTarget implements Disposable {
    private final GraphicsDevice device;
    private final TextureFormat colorFormat, depthFormat;
    private final TextureFilter filter;
    private final int samples;
    private Texture color, renderColor, depth;
    private RenderPassDescriptor clearPass, loadPass;
    private LoadOp clearColor = LoadOp.clear(0,0,0,0);
    private float depthClear = Float.NaN;
    private int width, height, revision;
    private boolean disposed;

    /** Creates an unallocated RGBA8 target, optionally with single-sample DEPTH32_FLOAT. */
    public OffscreenTarget(GraphicsDevice device, boolean depth) {
        this(device, TextureFormat.RGBA8_UNORM, depth ? TextureFormat.DEPTH32_FLOAT : null, 1, TextureFilter.LINEAR);
    }

    /** Creates an unallocated target. Null depthFormat disables depth; unsupported requests fail before allocation. */
    public OffscreenTarget(GraphicsDevice device, TextureFormat colorFormat, TextureFormat depthFormat,
            int samples, TextureFilter filter) {
        if (device == null || colorFormat == null || !colorFormat.isColor() || filter == null
                || depthFormat != null && !depthFormat.isDepthStencil()) {
            throw new FdxException("OffscreenTarget requires a device, color format, filter and optional depth format");
        }
        this.device=device; this.colorFormat=colorFormat; this.depthFormat=depthFormat;
        this.samples=samples; this.filter=filter;
        descriptor("offscreen color",1,1,colorFormat,1,TextureUsage.SAMPLED_RENDER_ATTACHMENT).validate(device.capabilities());
        descriptor("offscreen render color",1,1,colorFormat,samples,TextureUsage.RENDER_ATTACHMENT).validate(device.capabilities());
        if (samples > 1) {
            device.capabilities().require(GraphicsFeature.RESOLVE_ATTACHMENTS);
            if (!device.capabilities().supportsResolveFormat(colorFormat)) throw new FdxException("Cannot resolve "+colorFormat);
        }
        if (depthFormat != null) {
            device.capabilities().require(GraphicsFeature.EXPLICIT_DEPTH_STENCIL_ATTACHMENTS);
            descriptor("offscreen depth",1,1,depthFormat,samples,TextureUsage.RENDER_ATTACHMENT).validate(device.capabilities());
        }
    }

    /** Returns true only when resources change. Defer this call while the window has zero/minimized dimensions. */
    public boolean resize(int width, int height) {
        ensureOpen();
        if (width <= 0 || height <= 0) throw new FdxException("Offscreen dimensions must be positive");
        if (this.width == width && this.height == height) return false;
        Texture nextColor=null, nextRender=null, nextDepth=null;
        RenderPassDescriptor nextClear, nextLoad;
        float nextDepthClear=ClipDepthRange.getDefault().depthClearValue();
        try {
            nextColor=device.createTexture(descriptor("offscreen color",width,height,colorFormat,1,TextureUsage.SAMPLED_RENDER_ATTACHMENT));
            nextRender=samples == 1 ? nextColor : device.createTexture(descriptor("offscreen multisample color",
                    width,height,colorFormat,samples,TextureUsage.RENDER_ATTACHMENT));
            if (depthFormat != null) nextDepth=device.createTexture(descriptor("offscreen depth",width,height,depthFormat,
                    samples,TextureUsage.RENDER_ATTACHMENT));
            nextClear=pass(nextColor,nextRender,nextDepth,clearColor,true,nextDepthClear);
            nextLoad=pass(nextColor,nextRender,nextDepth,LoadOp.load(),false,nextDepthClear);
        } catch (RuntimeException | Error failure) {
            disposeTextures(nextColor,nextRender,nextDepth,failure);
            throw failure;
        }
        Texture oldColor=color, oldRender=renderColor, oldDepth=depth;
        color=nextColor; renderColor=nextRender; depth=nextDepth;
        clearPass=nextClear; loadPass=nextLoad; depthClear=nextDepthClear;
        this.width=width; this.height=height; revision++;
        disposeTextures(oldColor,oldRender,oldDepth,null);
        return true;
    }

    /** Sets the retained clear color in the target's attachment color space.
     * For transparent composition use transparent black, or RGB already multiplied by alpha. */
    public OffscreenTarget clearColor(float r, float g, float b, float a) {
        ensureOpen();
        if (!Float.isFinite(r) || !Float.isFinite(g) || !Float.isFinite(b) || !Float.isFinite(a) || a < 0 || a > 1) {
            throw new FdxException("Clear color must be finite with alpha in [0,1]");
        }
        clearColor=LoadOp.clear(r,g,b,a);
        if (color != null) clearPass=pass(color,renderColor,depth,clearColor,true,depthClear);
        return this;
    }

    /** Begins a borrowed pass which the caller must end. clear=true clears color and depth;
     * false preserves both. Every pass stores color/depth and resolves multisample color. */
    public RenderPass begin(GraphicsFrame frame, boolean clear) {
        ensureAllocated();
        if (frame == null) throw new FdxException("Active frame required");
        float next=ClipDepthRange.getDefault().depthClearValue();
        if (depth != null && next != depthClear) {
            depthClear=next;
            clearPass=pass(color,renderColor,depth,clearColor,true,next);
        }
        return frame.commandEncoder().beginRenderPass(clear ? clearPass : loadPass);
    }

    /** Borrowed resolved color; do not sample during a writing pass. */
    public Texture color() { ensureAllocated(); return color; }
    /** Borrowed render color, possibly multisampled; use color() when sampling. */
    public Texture renderColor() { ensureAllocated(); return renderColor; }
    /** Borrowed depth texture or null when disabled. */
    public Texture depth() { ensureAllocated(); return depth; }
    public int width() { return width; }
    public int height() { return height; }
    /** Changes after successful resize; rebuild borrowed regions when it changes. */
    public int revision() { return revision; }
    public int sampleCount() { return samples; }
    public TextureOrigin origin() { return device.capabilities().renderedTextureOrigin(); }
    /** Unpadded allocation estimate; excludes provider metadata. */
    public long estimatedBytes() {
        return (long)width * height * (colorFormat.bytesPerPixel() * (samples == 1 ? 1L : 1L+samples)
                + (depthFormat == null ? 0 : (long)depthFormat.bytesPerPixel()*samples));
    }
    private TextureDescriptor descriptor(String label,int w,int h,TextureFormat format,int count,TextureUsage usage) {
        return new TextureDescriptor().label(label).size(w,h).format(format).sampleCount(count).usage(usage)
                .filter(format.isDepthStencil() ? TextureFilter.NEAREST : filter);
    }
    private RenderPassDescriptor pass(Texture color,Texture render,Texture depth,LoadOp load,boolean clear,float clearDepth) {
        RenderPassColorAttachment attachment=color == render
                ? RenderPassColorAttachment.of(render.view(),load,StoreOp.store())
                : RenderPassColorAttachment.resolve(render.view(),color.view(),load,StoreOp.store());
        RenderPassDescriptor result=new RenderPassDescriptor().label("offscreen").colorAttachments(attachment);
        if (depth != null) result.depthStencilAttachment(RenderPassDepthStencilAttachment.of(depth.view(),
                clear ? LoadOp.clear(clearDepth,0,0,0) : LoadOp.load(),StoreOp.store(),
                clear ? LoadOp.clear(0,0,0,0) : LoadOp.load(),StoreOp.store()));
        result.validate(device.capabilities());
        return result;
    }
    private void ensureOpen() { if (disposed) throw new FdxException("OffscreenTarget disposed"); }
    private void ensureAllocated() { ensureOpen(); if (color == null) throw new FdxException("Resize target before use"); }
    @Override
    public boolean isDisposed() { return disposed; }
    @Override
    public void dispose() {
        if (disposed) return;
        disposed=true;
        Texture oldColor=color, oldRender=renderColor, oldDepth=depth;
        color=renderColor=depth=null; clearPass=loadPass=null;
        width=height=0;
        disposeTextures(oldColor,oldRender,oldDepth,null);
    }
    private static void disposeTextures(Texture color,Texture render,Texture depth,Throwable original) {
        Throwable failure=close(depth,original);
        if (render != color) failure=close(render,failure);
        failure=close(color,failure);
        if (original == null) {
            if (failure instanceof RuntimeException runtime) throw runtime;
            if (failure instanceof Error error) throw error;
        }
    }
    private static Throwable close(Texture texture,Throwable failure) {
        try { if (texture != null) texture.dispose(); }
        catch (RuntimeException | Error next) { if (failure == null) return next; if (next != failure) failure.addSuppressed(next); }
        return failure;
    }
}
